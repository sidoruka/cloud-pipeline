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

package com.epam.pipeline.controller.cluster;

import com.epam.pipeline.controller.ResponseResult;
import com.epam.pipeline.controller.Result;
import com.epam.pipeline.controller.vo.FilterNodesVO;
import com.epam.pipeline.entity.cluster.AllowedInstanceAndPriceTypes;
import com.epam.pipeline.entity.cluster.FilterPodsRequest;
import com.epam.pipeline.entity.cluster.InstanceType;
import com.epam.pipeline.entity.cluster.MasterNode;
import com.epam.pipeline.entity.cluster.NodeDisk;
import com.epam.pipeline.entity.cluster.NodeInstance;
import com.epam.pipeline.entity.cluster.monitoring.MonitoringStats;
import com.epam.pipeline.manager.cluster.ClusterApiService;
import com.epam.pipeline.test.creator.cluster.NodeCreatorUtils;
import com.epam.pipeline.test.web.AbstractControllerTest;
import com.epam.pipeline.util.ControllerTestUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MvcResult;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ClusterController.class)
public class ClusterControllerTest extends AbstractControllerTest {

    private static final long ID = 1L;
    private static final String CLUSTER_URL = SERVLET_PATH + "/cluster";
    private static final String MASTER_NODES_URL = CLUSTER_URL + "/master";
    private static final String NODE_URL = CLUSTER_URL + "/node";
    private static final String INSTANCE_URL = CLUSTER_URL + "/instance";
    private static final String LOAD_NODES_URL = NODE_URL + "/loadAll";
    private static final String FILTER_NODES_URL = NODE_URL +"/filter";
    private static final String LOAD_NODE_URL = NODE_URL + "/%s/load";
    private static final String NODE_NAME_URL = NODE_URL + "/%s";
    private static final String LOAD_INSTANCE_TYPES_URL = INSTANCE_URL + "/loadAll";
    private static final String LOAD_ALLOWED_INSTANCE_TYPES_URL = INSTANCE_URL + "/allowed";
    private static final String NODE_USAGE_URL = NODE_NAME_URL + "/usage";
    private static final String NODE_DISKS_URL = NODE_NAME_URL + "/disks";
    private static final String NODE_STATISTICS_URL = NODE_USAGE_URL + "/report";
    private static final String PORT = "7367";
    private static final String NAME = "testName";
    private static final String TEST_DATA = "test_data";
    private static final String FROM_STRING = "2019-04-01T09:08:07";
    private static final String TO_STRING = "2020-05-02T12:11:10";
    private NodeInstance nodeInstance;
    private List<NodeInstance> nodeInstances;
    private List<InstanceType> instanceTypes;
    private LocalDateTime from;
    private LocalDateTime to;

    @Autowired
    private ClusterApiService mockClusterApiService;

    @Before
    public void setUp() throws Exception {
        nodeInstance = NodeCreatorUtils.getDefaultNodeInstance();
        nodeInstances = Collections.singletonList(nodeInstance);

        instanceTypes = Collections.singletonList(NodeCreatorUtils.getDefaultInstanceType());

        from = LocalDateTime.parse(FROM_STRING, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        to = LocalDateTime.parse(TO_STRING, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }

    @Test
    public void shouldFailLoadMasterNodesForUnauthorizedUser() throws Exception {
        mvc().perform(get(MASTER_NODES_URL)
                .servletPath(SERVLET_PATH))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    public void shouldLoadMasterNodes() throws Exception {
        final List<MasterNode> masterNodes = Collections.singletonList(MasterNode.fromNode(
                NodeCreatorUtils.getDefaultNode(), PORT));

        Mockito.doReturn(masterNodes).when(mockClusterApiService).getMasterNodes();

        final MvcResult mvcResult = mvc().perform(get(MASTER_NODES_URL)
                .servletPath(SERVLET_PATH)
                .contentType(EXPECTED_CONTENT_TYPE))
                .andExpect(status().isOk())
                .andExpect(content().contentType(EXPECTED_CONTENT_TYPE))
                .andReturn();

        Mockito.verify(mockClusterApiService).getMasterNodes();

        final ResponseResult<List<MasterNode>> expectedResult = ControllerTestUtils.buildExpectedResult(masterNodes);

        ControllerTestUtils.assertResponse(mvcResult, getObjectMapper(), expectedResult,
                new TypeReference<Result<List<MasterNode>>>() { });
    }

    @Test
    public void shouldFailLoadNodesForUnauthorizedUser() throws Exception {
        mvc().perform(get(LOAD_NODES_URL)
                .servletPath(SERVLET_PATH))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    public void shouldLoadNodes() throws Exception {
        Mockito.doReturn(nodeInstances).when(mockClusterApiService).getNodes();

        final MvcResult mvcResult = mvc().perform(get(LOAD_NODES_URL)
                .servletPath(SERVLET_PATH)
                .contentType(EXPECTED_CONTENT_TYPE))
                .andExpect(status().isOk())
                .andExpect(content().contentType(EXPECTED_CONTENT_TYPE))
                .andReturn();

        Mockito.verify(mockClusterApiService).getNodes();

        final ResponseResult<List<NodeInstance>> expectedResult =
                ControllerTestUtils.buildExpectedResult(nodeInstances);

        ControllerTestUtils.assertResponse(mvcResult, getObjectMapper(), expectedResult,
                new TypeReference<Result<List<NodeInstance>>>() { });

    }

    @Test
    public void shouldFailFilterNodesForUnauthorizedUser() throws Exception {
        mvc().perform(post(FILTER_NODES_URL)
                .servletPath(SERVLET_PATH))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    public void shouldFilterNodes() throws Exception {
        final FilterNodesVO filterNodesVO = NodeCreatorUtils.getDefaultFilterNodesVO();

        Mockito.doReturn(nodeInstances).when(mockClusterApiService).filterNodes(Mockito.refEq(filterNodesVO));

        final MvcResult mvcResult = mvc().perform(post(FILTER_NODES_URL)
                .servletPath(SERVLET_PATH)
                .contentType(EXPECTED_CONTENT_TYPE)
                .content(getObjectMapper().writeValueAsString(filterNodesVO)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(EXPECTED_CONTENT_TYPE))
                .andReturn();

        Mockito.verify(mockClusterApiService).filterNodes(Mockito.refEq(filterNodesVO));

        final ResponseResult<List<NodeInstance>> expectedResult =
                ControllerTestUtils.buildExpectedResult(nodeInstances);

        ControllerTestUtils.assertResponse(mvcResult, getObjectMapper(), expectedResult,
                new TypeReference<Result<List<NodeInstance>>>() { });
    }

    @Test
    public void shouldFailLoadNodeForUnauthorizedUser() throws Exception {
        mvc().perform(get(String.format(LOAD_NODE_URL, NAME))
                .servletPath(SERVLET_PATH))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    public void shouldLoadNode() throws Exception {
        Mockito.doReturn(nodeInstance).when(mockClusterApiService).getNode(NAME);

        final MvcResult mvcResult = mvc().perform(get(String.format(LOAD_NODE_URL, NAME))
                .servletPath(SERVLET_PATH)
                .contentType(EXPECTED_CONTENT_TYPE))
                .andExpect(status().isOk())
                .andExpect(content().contentType(EXPECTED_CONTENT_TYPE))
                .andReturn();

        Mockito.verify(mockClusterApiService).getNode(NAME);

        final ResponseResult<NodeInstance> expectedResult =
                ControllerTestUtils.buildExpectedResult(nodeInstance);

        ControllerTestUtils.assertResponse(mvcResult, getObjectMapper(), expectedResult,
                new TypeReference<Result<NodeInstance>>() { });
    }

    @Test
    public void shouldFailLoadNodeFilteredForUnauthorizedUser() throws Exception {
        mvc().perform(post(String.format(LOAD_NODE_URL, NAME))
                .servletPath(SERVLET_PATH))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    public void shouldLoadNodeFiltered() throws Exception {
        final FilterPodsRequest filterPodsRequest = NodeCreatorUtils.getDefaultFilterPodsRequest();

        Mockito.doReturn(nodeInstance).when(mockClusterApiService)
                .getNode(Mockito.eq(NAME), Mockito.refEq(filterPodsRequest));

        final MvcResult mvcResult = mvc().perform(post(String.format(LOAD_NODE_URL, NAME))
                .servletPath(SERVLET_PATH)
                .contentType(EXPECTED_CONTENT_TYPE)
                .content(getObjectMapper().writeValueAsString(filterPodsRequest)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(EXPECTED_CONTENT_TYPE))
                .andReturn();

        Mockito.verify(mockClusterApiService)
                .getNode(Mockito.eq(NAME), Mockito.refEq(filterPodsRequest));

        final ResponseResult<NodeInstance> expectedResult =
                ControllerTestUtils.buildExpectedResult(nodeInstance);

        ControllerTestUtils.assertResponse(mvcResult, getObjectMapper(), expectedResult,
                new TypeReference<Result<NodeInstance>>() { });
    }

    @Test
    public void shouldFailTerminateNodeForUnauthorizedUser() throws Exception {
        mvc().perform(delete(String.format(NODE_NAME_URL, NAME))
                .servletPath(SERVLET_PATH))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    public void shouldTerminateNode() throws Exception {
        Mockito.doReturn(nodeInstance).when(mockClusterApiService).terminateNode(NAME);

        final MvcResult mvcResult = mvc().perform(delete(String.format(NODE_NAME_URL, NAME))
                .servletPath(SERVLET_PATH)
                .contentType(EXPECTED_CONTENT_TYPE))
                .andExpect(status().isOk())
                .andExpect(content().contentType(EXPECTED_CONTENT_TYPE))
                .andReturn();

        Mockito.verify(mockClusterApiService).terminateNode(NAME);

        final ResponseResult<NodeInstance> expectedResult =
                ControllerTestUtils.buildExpectedResult(nodeInstance);

        ControllerTestUtils.assertResponse(mvcResult, getObjectMapper(), expectedResult,
                new TypeReference<Result<NodeInstance>>() { });
    }

    @Test
    public void shouldFailLoadAllInstanceTypesForUnauthorizedUser() throws Exception {
        mvc().perform(get(LOAD_INSTANCE_TYPES_URL)
                .servletPath(SERVLET_PATH))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    public void shouldLoadAllowedInstanceTypes() throws Exception {
        Mockito.doReturn(instanceTypes).when(mockClusterApiService).getAllowedInstanceTypes(ID, true);

        final MvcResult mvcResult = mvc().perform(get(LOAD_INSTANCE_TYPES_URL)
                .servletPath(SERVLET_PATH)
                .contentType(EXPECTED_CONTENT_TYPE)
                .param("regionId", "1")
                .param("toolInstances", "false")
                .param("spot", "true"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(EXPECTED_CONTENT_TYPE))
                .andReturn();

        Mockito.verify(mockClusterApiService).getAllowedInstanceTypes(ID, true);

        final ResponseResult<List<InstanceType>> expectedResult =
                ControllerTestUtils.buildExpectedResult(instanceTypes);

        ControllerTestUtils.assertResponse(mvcResult, getObjectMapper(), expectedResult,
                new TypeReference<Result<List<InstanceType>>>() { });
    }

    @Test
    @WithMockUser
    public void shouldLoadAllowedToolInstanceTypes() throws Exception {
        Mockito.doReturn(instanceTypes).when(mockClusterApiService).getAllowedToolInstanceTypes(ID, false);

        final MvcResult mvcResult = mvc().perform(get(LOAD_INSTANCE_TYPES_URL)
                .servletPath(SERVLET_PATH)
                .contentType(EXPECTED_CONTENT_TYPE)
                .param("regionId", "1")
                .param("toolInstances", "true")
                .param("spot", "false"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(EXPECTED_CONTENT_TYPE))
                .andReturn();

        Mockito.verify(mockClusterApiService).getAllowedToolInstanceTypes(ID, false);

        final ResponseResult<List<InstanceType>> expectedResult =
                ControllerTestUtils.buildExpectedResult(instanceTypes);

        ControllerTestUtils.assertResponse(mvcResult, getObjectMapper(), expectedResult,
                new TypeReference<Result<List<InstanceType>>>() { });
    }

    @Test
    public void shouldFailLoadAllowedInstanceAndPriceTypesForUnauthorizedUser() throws Exception {
        mvc().perform(get(LOAD_ALLOWED_INSTANCE_TYPES_URL)
                .servletPath(SERVLET_PATH))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    public void shouldLoadAllowedInstanceAndPriceTypes() throws Exception {
        final AllowedInstanceAndPriceTypes allowedInstanceAndPriceTypes =
                NodeCreatorUtils.getDefaultAllowedInstanceAndPriceTypes();

        Mockito.doReturn(allowedInstanceAndPriceTypes).when(mockClusterApiService)
                .getAllowedInstanceAndPriceTypes(ID, ID, false);

        final MvcResult mvcResult = mvc().perform(get(LOAD_ALLOWED_INSTANCE_TYPES_URL)
                .servletPath(SERVLET_PATH)
                .contentType(EXPECTED_CONTENT_TYPE)
                .param("toolId", "1")
                .param("regionId", "1")
                .param("spot", "false"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(EXPECTED_CONTENT_TYPE))
                .andReturn();

        Mockito.verify(mockClusterApiService).getAllowedInstanceAndPriceTypes(ID, ID, false);

        final ResponseResult<AllowedInstanceAndPriceTypes> expectedResult =
                ControllerTestUtils.buildExpectedResult(allowedInstanceAndPriceTypes);

        ControllerTestUtils.assertResponse(mvcResult, getObjectMapper(), expectedResult,
                new TypeReference<Result<AllowedInstanceAndPriceTypes>>() { });
    }

    @Test
    public void shouldFailGetNodeUsageStatisticsForUnauthorizedUser() throws Exception {
        mvc().perform(get(String.format(NODE_USAGE_URL, NAME))
                .servletPath(SERVLET_PATH))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    public void shouldGetNodeUsageStatistics() throws Exception {
        final List<MonitoringStats> monitoringStats = Collections.singletonList(new MonitoringStats());

        Mockito.doReturn(monitoringStats).when(mockClusterApiService)
                .getStatsForNode(NAME, from, to);

        final MvcResult mvcResult = mvc().perform(get(String.format(NODE_USAGE_URL, NAME))
                .servletPath(SERVLET_PATH)
                .content(EXPECTED_CONTENT_TYPE)
                .param("from", FROM_STRING.replace('T', ' '))
                .param("to", TO_STRING.replace('T', ' ')))
                .andExpect(status().isOk())
                .andExpect(content().contentType(EXPECTED_CONTENT_TYPE))
                .andReturn();

        Mockito.verify(mockClusterApiService).getStatsForNode(NAME, from, to);

        final ResponseResult<List<MonitoringStats>> expectedResult =
                ControllerTestUtils.buildExpectedResult(monitoringStats);

        ControllerTestUtils.assertResponse(mvcResult, getObjectMapper(), expectedResult,
                new TypeReference<Result<List<MonitoringStats>>>() { });
    }

    @Test
    public void shouldFailDownloadNodeUsageStatisticsReportForUnauthorizedUser() throws Exception {
        mvc().perform(get(String.format(NODE_STATISTICS_URL, NAME))
                .servletPath(SERVLET_PATH))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    public void shouldDownloadNodeUsageStatisticsReport() throws Exception {
        final InputStream inputStream = new ByteArrayInputStream(TEST_DATA.getBytes());
        Mockito.doReturn(inputStream).when(mockClusterApiService)
                .getUsageStatisticsFile(NAME, from, to, Duration.ofHours(1));
        final MvcResult mvcResult = mvc().perform(get(String.format(NODE_STATISTICS_URL, NAME))
                .servletPath(SERVLET_PATH)
                .contentType("application/octet-stream")
                .param("from", FROM_STRING.replace('T', ' '))
                .param("to", TO_STRING.replace('T', ' '))
                .param("interval", "PT1H"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/octet-stream"))
                .andReturn();

        Mockito.verify(mockClusterApiService).getUsageStatisticsFile(NAME, from, to, Duration.ofHours(1));

        String actualResponseData = mvcResult.getResponse().getContentAsString();
        Assert.assertEquals(TEST_DATA, actualResponseData);
    }

    @Test
    public void shouldFailLoadNodeDisksForUnauthorizedUser() throws Exception {
        mvc().perform(get(String.format(NODE_DISKS_URL, NAME))
                .servletPath(SERVLET_PATH))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    public void shouldLoadNodeDisks() throws Exception {
        final List<NodeDisk> nodeDisks = Collections.singletonList(NodeCreatorUtils.getDefaultNodeDisk());

        Mockito.doReturn(nodeDisks).when(mockClusterApiService).loadNodeDisks(NAME);

        final MvcResult mvcResult = mvc().perform(get(String.format(NODE_DISKS_URL, NAME))
                .servletPath(SERVLET_PATH)
                .contentType(EXPECTED_CONTENT_TYPE))
                .andExpect(status().isOk())
                .andExpect(content().contentType(EXPECTED_CONTENT_TYPE))
                .andReturn();

        Mockito.verify(mockClusterApiService).loadNodeDisks(NAME);

        final ResponseResult<List<NodeDisk>> expectedResult = ControllerTestUtils.buildExpectedResult(nodeDisks);

        ControllerTestUtils.assertResponse(mvcResult, getObjectMapper(), expectedResult,
                new TypeReference<Result<List<NodeDisk>>>() { });
    }
}
