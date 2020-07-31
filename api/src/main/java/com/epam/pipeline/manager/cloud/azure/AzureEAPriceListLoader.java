/*
 * Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.epam.pipeline.manager.cloud.azure;

import com.epam.pipeline.entity.cluster.InstanceOffer;
import com.epam.pipeline.entity.pricing.azure.AzureEAPricingMeter;
import com.epam.pipeline.entity.pricing.azure.AzureEAPricingResult;
import com.epam.pipeline.entity.region.AbstractCloudRegion;
import com.epam.pipeline.entity.region.AzureRegion;
import com.epam.pipeline.entity.region.CloudProvider;
import com.epam.pipeline.manager.cloud.CloudInstancePriceService;
import com.epam.pipeline.manager.datastorage.providers.azure.AzureHelper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.azure.credentials.ApplicationTokenCredentials;
import com.microsoft.azure.credentials.AzureTokenCredentials;
import com.microsoft.azure.management.Azure;
import com.microsoft.azure.management.compute.ComputeResourceType;
import com.microsoft.azure.management.compute.ResourceSkuCapabilities;
import com.microsoft.azure.management.compute.ResourceSkuRestrictionsReasonCode;
import com.microsoft.azure.management.compute.implementation.ResourceSkuInner;
import com.microsoft.azure.management.resources.fluentcore.arm.Region;
import com.microsoft.azure.management.resources.fluentcore.model.HasInner;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang.StringUtils;
import org.springframework.util.Assert;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.epam.pipeline.manager.cloud.CloudInstancePriceService.TermType;
import static com.epam.pipeline.manager.cloud.azure.AzurePricingClient.executeRequest;

@Slf4j
public class AzureEAPriceListLoader {

    private static final String API_VERSION = "2019-10-01";
    private static final String WINDOWS_OS = "Windows";
    private static final String LINUX_OS = "Linux";
    private static final String V_CPU_CAPABILITY = "vCPUs";
    private static final String GPU_CAPABILITY = "GPUs";
    private static final String MEMORY_CAPABILITY = "MemoryGB";
    private static final String IS_PREMIUM_CAPABILITY = "PremiumIO";
    private static final String DISK_SIZE_CAPABILITY = "MaxSizeGiB";
    private static final String PROMO = "Promo";
    private static final String STANDARD = "Standard";
    private static final String BASIC = "Basic";
    private static final String DISK_INDICATOR = "Disks";
    private static final String VIRTUAL_MACHINES_CATEGORY = "Virtual Machines";
    private static final String DISKS_CATEGORY = "Storage";
    private static final String LOW_PRIORITY_VM_POSTFIX = " Low Priority";
    private static final String DELIMITER = "/";
    private static final int READ_TIMEOUT = 60;
    private static final int CONNECT_TIMEOUT = 60;
    private static final String GENERAL_PURPOSE_FAMILY = "General purpose";
    private static final String GPU_FAMILY = "GPU instance";
    private static final double DEFAULT_PRICE = 0.0;
    private static final String LOW_PRIORITY_CAPABLE = "LowPriorityCapable";
    private static final String TRUE = "True";
    private static final String DEFAULT_DISK_UNIT = "1/Month";

    private final AzurePricingClient azurePricingClient;
    private final String meterRegionName;
    private final String azureApiUrl;
    private final String authPath;

    public AzureEAPriceListLoader(final String authPath, String meterRegionName, final String azureApiUrl) {
        this.authPath = authPath;
        this.meterRegionName = meterRegionName;
        this.azureApiUrl = azureApiUrl;
        this.azurePricingClient = buildRetrofitClient(azureApiUrl);
    }

    public List<InstanceOffer> load(final AbstractCloudRegion region) throws IOException {
        final AzureTokenCredentials credentials = getAzureCredentials();
        final Azure client = AzureHelper.buildClient(authPath);

        final Map<String, ResourceSkuInner> vmSkusByName = client.computeSkus()
                .listbyRegionAndResourceType(Region.fromName(region.getRegionCode()),
                        ComputeResourceType.VIRTUALMACHINES)
                .stream()
                .map(HasInner::inner)
                .filter(sku -> Objects.nonNull(sku.name()) && isAvailableForSubscription(sku))
                .collect(Collectors.toMap(ResourceSkuInner::name, Function.identity()));

        final Map<String, ResourceSkuInner> diskSkusByName = client.computeSkus()
                .listByResourceType(ComputeResourceType.DISKS)
                .stream()
                .map(HasInner::inner)
                .filter(sku -> Objects.nonNull(sku.size()) && isAvailableForSubscription(sku))
                .collect(Collectors.toMap(ResourceSkuInner::size, Function.identity(), (o1, o2) -> o1));

        final Optional<AzureEAPricingResult> prices = getPricing(client.subscriptionId(), credentials);
        return prices.filter(p -> CollectionUtils.isNotEmpty(p.getProperties().getPricesheets()))
                .map(p -> mergeSkusWithPrices(p.getProperties().getPricesheets(), vmSkusByName, diskSkusByName,
                        meterRegionName, region.getId()))
                .orElseGet(() -> getOffersFromSku(vmSkusByName, diskSkusByName, region.getId()));
    }

    private AzureTokenCredentials getAzureCredentials() throws IOException {
        if (StringUtils.isBlank(authPath)) {
            return AzureHelper.getAzureCliCredentials();
        }
        return ApplicationTokenCredentials.fromFile(new File(authPath));
    }

    private List<InstanceOffer> getOffersFromSku(final Map<String, ResourceSkuInner> vmSkusByName,
                                                 final Map<String, ResourceSkuInner> diskSkusByName,
                                                 final Long regionId) {
        log.debug("Azure prices are not available. Instance offers will be loaded without price.");
        final Stream<InstanceOffer> onDemandVmOffers = MapUtils.emptyIfNull(vmSkusByName)
                .values()
                .stream()
                .map(sku -> vmSkuToOffer(regionId, sku, DEFAULT_PRICE, sku.name(), TermType.ON_DEMAND, LINUX_OS));

        final Stream<InstanceOffer> lowPriorityVmOffers = MapUtils.emptyIfNull(vmSkusByName)
                .values()
                .stream()
                .filter(this::isLowPriorityAvailable)
                .map(sku -> vmSkuToOffer(regionId, sku, DEFAULT_PRICE,
                        sku.name() + LOW_PRIORITY_VM_POSTFIX,
                        TermType.LOW_PRIORITY, LINUX_OS));

        final Stream<InstanceOffer> diskOffers = MapUtils.emptyIfNull(diskSkusByName)
                .values()
                .stream()
                .map(sku -> diskSkuToOffer(regionId, sku, DEFAULT_PRICE, DEFAULT_DISK_UNIT));

        return Stream.concat(Stream.concat(onDemandVmOffers, lowPriorityVmOffers), diskOffers)
                .collect(Collectors.toList());
    }

    private boolean isLowPriorityAvailable(final ResourceSkuInner sku) {
        return ListUtils.emptyIfNull(sku.capabilities())
                .stream()
                .anyMatch(capability -> LOW_PRIORITY_CAPABLE.equals(capability.name()) &&
                        TRUE.equals(capability.value()));
    }

    private boolean isAvailableForSubscription(final ResourceSkuInner sku) {
        return ListUtils.emptyIfNull(sku.restrictions())
                .stream()
                .noneMatch(restriction -> restriction.reasonCode()
                        .equals(ResourceSkuRestrictionsReasonCode.NOT_AVAILABLE_FOR_SUBSCRIPTION));
    }

    private AzurePricingClient buildRetrofitClient(final String azureApiUrl) {
        final OkHttpClient client = new OkHttpClient.Builder()
                .followRedirects(true)
                .readTimeout(READ_TIMEOUT, TimeUnit.SECONDS)
                .connectTimeout(CONNECT_TIMEOUT, TimeUnit.SECONDS)
                .hostnameVerifier((s, sslSession) -> true)
                .build();
        final ObjectMapper mapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        final Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(azureApiUrl)
                .addConverterFactory(JacksonConverterFactory.create(mapper))
                .client(client)
                .build();
        return retrofit.create(AzurePricingClient.class);
    }

    private Optional<AzureEAPricingResult> getPricing(final String subscription,
                                                      final AzureTokenCredentials credentials) throws IOException {
        Assert.isTrue(StringUtils.isNotBlank(subscription), "Could not find subscription ID");
        return Optional.of(getPricesheet(subscription, credentials));
    }

    private AzureEAPricingResult getPricesheet(String subscription, AzureTokenCredentials credentials) throws IOException {
        return new AzureEAPricingResult(
                new AzureEAPricingResult.PricingProperties(
                    null,
                    getPricesheet(new ArrayList<>(), subscription, credentials, null)
                            .stream()
                            .filter(details -> details.getMeterDetails().getMeterLocation().equals(meterRegionName))
                            .filter(azureEAPricingMeter -> azureEAPricingMeter.getCurrencyCode().equals(CloudInstancePriceService.CURRENCY))
                            .collect(Collectors.toList())
                )
        );
    }

    private List<AzureEAPricingMeter> getPricesheet(List<AzureEAPricingMeter> buffer, String subscription,
                                                    AzureTokenCredentials credentials, String skiptoken
    ) throws IOException {
        final String token = credentials.getToken(azureApiUrl);
        Assert.isTrue(StringUtils.isNotBlank(token), "Could not find access token");
        AzureEAPricingResult meterDetails = executeRequest(azurePricingClient.getPricesheet(
                "Bearer " + token, subscription, API_VERSION, "meterDetails", 10000, skiptoken));
        if (meterDetails != null && meterDetails.getProperties() != null) {
            buffer.addAll(meterDetails.getProperties().getPricesheets());
            if (StringUtils.isNotBlank(meterDetails.getProperties().getNextLink())) {
                Pattern pattern = Pattern.compile(".*skiptoken=([^&]+).*");
                Matcher matcher = pattern.matcher(meterDetails.getProperties().getNextLink());
                if (matcher.matches()) {
                    skiptoken = matcher.group(1);
                    return getPricesheet(buffer, subscription, credentials, skiptoken);
                }
            }
        }
        return buffer;
    }

    List<InstanceOffer> mergeSkusWithPrices(final List<AzureEAPricingMeter> prices,
                                            final Map<String, ResourceSkuInner> virtualMachineSkus,
                                            final Map<String, ResourceSkuInner> diskSkusByName,
                                            final String meterRegion, final Long regionId) {
        return prices.stream()
                .filter(meter -> meterRegion.equalsIgnoreCase(meter.getMeterDetails().getMeterLocation()))
                .filter(this::verifyPricingMeters)
                .flatMap(meter -> {
                    if (virtualMachinesCategory(meter)) {
                        return getVmSizes(meter.getMeterDetails().getMeterName())
                                .stream()
                                .map(vmSize -> buildVmInstanceOffer(virtualMachineSkus, meter, vmSize, regionId));
                    }
                    if (diskCategory(meter)) {
                        return Stream.of(buildDiskInstanceOffer(diskSkusByName, meter, regionId));
                    }
                    return null;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private boolean verifyPricingMeters(final AzureEAPricingMeter meter) {
        return StringUtils.isNotBlank(meter.getMeterDetails().getMeterCategory())
                && StringUtils.isNotBlank(meter.getMeterId())
                && StringUtils.isNotBlank(meter.getMeterDetails().getMeterName())
                && StringUtils.isNotBlank(meter.getMeterDetails().getMeterSubCategory())
                && StringUtils.isNotBlank(meter.getMeterDetails().getMeterLocation())
                && meter.getUnitPrice() > 0.f;
    }

    private InstanceOffer buildDiskInstanceOffer(final Map<String, ResourceSkuInner> diskSkusByName,
                                                 final AzureEAPricingMeter meter, final Long regionId) {
        final ResourceSkuInner diskSku = getDiskSku(diskSkusByName, meter.getMeterDetails().getMeterName());
        if (diskSku == null) {
            return null;
        }
        return diskSkuToOffer(regionId, diskSku,
                meter.getUnitPrice() / getNumberOfUnits(meter.getUnitOfMeasure()), DEFAULT_DISK_UNIT);
    }

    private InstanceOffer diskSkuToOffer(final Long regionId,
                                         final ResourceSkuInner diskSku,
                                         final double price,
                                         final String unit) {
        final Map<String, String> capabilitiesByName = ListUtils.emptyIfNull(diskSku.capabilities())
                .stream()
                .collect(Collectors.toMap(ResourceSkuCapabilities::name, ResourceSkuCapabilities::value));
        return InstanceOffer.builder()
                .cloudProvider(CloudProvider.AZURE)
                .tenancy(CloudInstancePriceService.SHARED_TENANCY)
                .productFamily(CloudInstancePriceService.STORAGE_PRODUCT_FAMILY)
                .sku(diskSku.size())
                .priceListPublishDate(new Date())
                .currency(CloudInstancePriceService.CURRENCY)
                .pricePerUnit(price)
                .regionId(regionId)
                .unit(unit)
                .volumeType(CloudInstancePriceService.GENERAL_PURPOSE_VOLUME_TYPE)
                .memory(Float.parseFloat(capabilitiesByName.getOrDefault(DISK_SIZE_CAPABILITY, "0")))
                .build();
    }

    private InstanceOffer buildVmInstanceOffer(final Map<String, ResourceSkuInner> virtualMachineSkus,
                                               final AzureEAPricingMeter meter, final String vmSize,
                                               final Long regionId) {
        final String subCategory = meter.getMeterDetails().getMeterSubCategory();
        final ResourceSkuInner resourceSku = getVirtualMachineSku(virtualMachineSkus, vmSize, subCategory);
        if (resourceSku == null) {
            return null;
        }
        return vmSkuToOffer(regionId, resourceSku,
                meter.getUnitPrice() / getNumberOfUnits(meter.getUnitOfMeasure()),
                meter.getMeterId(),
                meter.getMeterDetails().getMeterName().contains(LOW_PRIORITY_VM_POSTFIX)
                        ? TermType.LOW_PRIORITY
                        : TermType.ON_DEMAND,
                getOperatingSystem(meter.getMeterDetails().getMeterSubCategory()));
    }

    private int getNumberOfUnits(String unitOfMeasure) {
        return Integer.parseInt(unitOfMeasure.split(" ")[0]);
    }

    private InstanceOffer vmSkuToOffer(final Long regionId,
                                       final ResourceSkuInner resourceSku,
                                       final double price,
                                       final String sku,
                                       final TermType termType,
                                       final String os) {
        final List<ResourceSkuCapabilities> capabilities = resourceSku.capabilities();
        final Map<String, String> capabilitiesByName = capabilities.stream()
                .collect(Collectors.toMap(ResourceSkuCapabilities::name,
                        ResourceSkuCapabilities::value));
        final int gpu = Integer.parseInt(capabilitiesByName.getOrDefault(GPU_CAPABILITY, "0"));
        return InstanceOffer.builder()
                .cloudProvider(CloudProvider.AZURE)
                .termType(termType.getName())
                .tenancy(CloudInstancePriceService.SHARED_TENANCY)
                .productFamily(CloudInstancePriceService.INSTANCE_PRODUCT_FAMILY)
                .sku(sku)
                .priceListPublishDate(new Date())
                .currency(CloudInstancePriceService.CURRENCY)
                .instanceType(resourceSku.name())
                .pricePerUnit(price)
                .regionId(regionId)
                .unit(CloudInstancePriceService.HOURS_UNIT)
                .volumeType(getDiskType(capabilitiesByName.getOrDefault(IS_PREMIUM_CAPABILITY, "false")))
                .operatingSystem(os)
                .instanceFamily(gpu == 0 ? GENERAL_PURPOSE_FAMILY : GPU_FAMILY)
                .vCPU(Integer.parseInt(capabilitiesByName.getOrDefault(V_CPU_CAPABILITY, "0")))
                .gpu(gpu)
                .memory(Float.parseFloat(capabilitiesByName.getOrDefault(MEMORY_CAPABILITY, "0")))
                .build();
    }

    private String getDiskType(final String isPremiumSupport) {
        return isPremiumSupport.equalsIgnoreCase("true") ? "Premium" : "Standard";
    }

    private boolean virtualMachinesCategory(final AzureEAPricingMeter meter) {
        return meter.getMeterDetails().getMeterCategory().equals(VIRTUAL_MACHINES_CATEGORY)
                && meter.getMeterDetails().getMeterSubCategory().contains("Series");
    }

    private boolean diskCategory(final AzureEAPricingMeter meter) {
        return meter.getMeterDetails().getMeterCategory().equals(DISKS_CATEGORY) && meter.getMeterDetails().getMeterSubCategory().contains("SSD")
                && meter.getMeterDetails().getMeterSubCategory().contains(DISK_INDICATOR)
                && meter.getMeterDetails().getMeterName().contains(DISK_INDICATOR);
    }

    private ResourceSkuInner getVirtualMachineSku(final Map<String, ResourceSkuInner> virtualMachineSkus,
                                                  final String vmSize, final String subCategory) {
        String vmSkusKey = (subCategory.contains(BASIC) ? BASIC : STANDARD) + "_" + vmSize;

        if (subCategory.contains(PROMO)) {
            vmSkusKey += "_" + PROMO;
        }

        return virtualMachineSkus.get(vmSkusKey);
    }

    private ResourceSkuInner getDiskSku(final Map<String, ResourceSkuInner> disksSkus,
                                        final String diskName) {
        final String diskSkusKey = diskName.replace(DISK_INDICATOR, "").trim();
        return disksSkus.get(diskSkusKey);
    }

    private String getOperatingSystem(final String subCategory) {
        return subCategory.trim().endsWith(WINDOWS_OS) ? WINDOWS_OS : LINUX_OS;
    }

    private List<String> getVmSizes(final String rawMeterName) {
        return Arrays.stream(rawMeterName.split(DELIMITER))
                .map(vmSize -> vmSize.trim()
                        .replaceAll(LOW_PRIORITY_VM_POSTFIX, StringUtils.EMPTY)
                        .replaceAll(" ", "_"))
                .collect(Collectors.toList());
    }
}
