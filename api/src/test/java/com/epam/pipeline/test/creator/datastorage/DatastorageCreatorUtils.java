/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.test.creator.datastorage;

import com.epam.pipeline.controller.vo.DataStorageVO;
import com.epam.pipeline.controller.vo.data.storage.UpdateDataStorageItemVO;
import com.epam.pipeline.entity.datastorage.DataStorageAction;
import com.epam.pipeline.entity.datastorage.DataStorageDownloadFileUrl;
import com.epam.pipeline.entity.datastorage.DataStorageFile;
import com.epam.pipeline.entity.datastorage.DataStorageItemContent;
import com.epam.pipeline.entity.datastorage.DataStorageListing;
import com.epam.pipeline.entity.datastorage.DataStorageStreamingContent;
import com.epam.pipeline.entity.datastorage.DataStorageWithShareMount;
import com.epam.pipeline.entity.datastorage.FileShareMount;
import com.epam.pipeline.entity.datastorage.PathDescription;
import com.epam.pipeline.entity.datastorage.StorageMountPath;
import com.epam.pipeline.entity.datastorage.StorageUsage;
import com.epam.pipeline.entity.datastorage.TemporaryCredentials;
import com.epam.pipeline.entity.datastorage.aws.S3bucketDataStorage;
import com.epam.pipeline.entity.datastorage.rules.DataStorageRule;

import java.io.InputStream;
import java.util.Collections;
import java.util.List;

import static com.epam.pipeline.test.creator.CommonCreatorConstants.ID;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.TEST_STRING;

public final class DatastorageCreatorUtils {

    private DatastorageCreatorUtils() {

    }

    public static S3bucketDataStorage getS3bucketDataStorage() {
        return new S3bucketDataStorage(ID, TEST_STRING, TEST_STRING);
    }

    public static S3bucketDataStorage getS3bucketDataStorage(Long id, String owner) {
        final S3bucketDataStorage s3bucket = new S3bucketDataStorage(id, TEST_STRING, TEST_STRING);
        s3bucket.setOwner(owner);
        return s3bucket;
    }

    public static S3bucketDataStorage getS3bucketDataStorage(Long id, String owner, boolean shared) {
        final S3bucketDataStorage s3bucket = new S3bucketDataStorage(id, TEST_STRING, TEST_STRING);
        s3bucket.setOwner(owner);
        s3bucket.setShared(shared);
        return s3bucket;
    }

    public static DataStorageWithShareMount getDataStorageWithShareMount() {
        return new DataStorageWithShareMount(getS3bucketDataStorage(), new FileShareMount());
    }

    public static DataStorageListing getDataStorageListing() {
        return new DataStorageListing();
    }

    public static DataStorageFile getDataStorageFile() {
        return new DataStorageFile();
    }

    public static UpdateDataStorageItemVO getUpdateDataStorageItemVO() {
        return new UpdateDataStorageItemVO();
    }

    public static DataStorageDownloadFileUrl getDataStorageDownloadFileUrl() {
        return new DataStorageDownloadFileUrl();
    }

    public static DataStorageRule getDataStorageRule() {
        DataStorageRule dataStorageRule = new DataStorageRule();
        dataStorageRule.setPipelineId(ID);
        return dataStorageRule;
    }

    public static DataStorageItemContent getDefaultDataStorageItemContent() {
        return new DataStorageItemContent();
    }

    public static DataStorageStreamingContent getDefaultDataStorageStreamingContent(InputStream inputStream) {
        return new DataStorageStreamingContent(inputStream, TEST_STRING);
    }

    public static PathDescription getPathDescription() {
        return new PathDescription();
    }

    public static StorageUsage getStorageUsage() {
        return StorageUsage.builder().build();
    }

    public static StorageMountPath getStorageMountPath() {
        return new StorageMountPath(TEST_STRING, getS3bucketDataStorage(), new FileShareMount());
    }

    public static FileShareMount getFileShareMount() {
        return new FileShareMount();
    }

    public static DataStorageVO getDataStorageVO() {
        DataStorageVO dataStorageVO = new DataStorageVO();
        dataStorageVO.setId(ID);
        return dataStorageVO;
    }

    public static TemporaryCredentials getTemporaryCredentials() {
        return new TemporaryCredentials();
    }

    public static DataStorageAction getDataStorageAction() {
        final DataStorageAction dataStorageAction = new DataStorageAction();
        dataStorageAction.setId(ID);
        return dataStorageAction;
    }

    public static List<UpdateDataStorageItemVO> getUpdateDataStorageItemVOList() {
        return Collections.singletonList(getUpdateDataStorageItemVO());
    }

    public static List<DataStorageFile> getDataStorageFileList() {
        return Collections.singletonList(getDataStorageFile());
    }

    public static List<DataStorageDownloadFileUrl> getDataStorageDownloadFileUrlList() {
        return Collections.singletonList(getDataStorageDownloadFileUrl());
    }

    public static List<DataStorageRule> getDataStorageRuleList() {
        return Collections.singletonList(getDataStorageRule());
    }

    public static List<PathDescription> getPathDescriptionList() {
        return Collections.singletonList(getPathDescription());
    }

    public static List<DataStorageAction> getDataStorageActionList() {
        return Collections.singletonList(getDataStorageAction());
    }
}
