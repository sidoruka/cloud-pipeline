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
package com.epam.pipeline.autotests;

import com.codeborne.selenide.Configuration;
import com.codeborne.selenide.Selenide;
import com.codeborne.selenide.WebDriverRunner;
import com.epam.pipeline.autotests.ao.ToolTab;
import com.epam.pipeline.autotests.mixins.Authorization;
import com.epam.pipeline.autotests.mixins.Navigation;
import com.epam.pipeline.autotests.utils.C;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Properties;

import static com.codeborne.selenide.Selenide.open;
import static com.codeborne.selenide.WebDriverRunner.getWebDriver;

public class ParallelLoadTests extends AbstractSeveralPipelineRunningTest implements Navigation, Authorization {

    public static final String CONF_PATH_PROPERTY = "com.epam.bfx.e2e.ui.property.path";
    public static final int userCount;
    public static final Object[][] userList;
    private static final String PARALLEL_TEST_FOLDER = "parallelTestFolder";

    static {
        String propFilePath = System.getProperty(CONF_PATH_PROPERTY, "parallelLoad.conf");
        Properties conf = new Properties();

        try {
            conf.load(new FileInputStream(propFilePath));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        userCount = conf.size() / 2;
        ArrayList<Object[]> dataList = new ArrayList<>();
        for (int i = 1; i <= userCount; i++) {
            dataList.add(new Object[]{conf.getProperty("e2e.ui.login" + i), conf.getProperty("e2e.ui.pass" + i)});
        }
        userList = dataList.toArray(new Object[dataList.size()][]);
    }

    @DataProvider(name = "openNewBrowser", parallel = true)
    public static Object[][] openNewBrowser() {
        return userList;
    }

    @BeforeClass
    public void addUsers() {
        setUp();
        loginAs(admin);
        Arrays.stream(userList)
            .forEach(this::addUser);
        library()
                .createFolder(PARALLEL_TEST_FOLDER);
        closeDriverObjects();
    }

    @AfterMethod(alwaysRun=true)
    public static void closeDriverObjects(){
        getWebDriver().close();
    }

    @BeforeMethod(alwaysRun = true)
    public void setUp() {
        Configuration.timeout = C.DEFAULT_TIMEOUT;
        Configuration.browser = WebDriverRunner.CHROME;
        Configuration.startMaximized = true;
        System.setProperty("webdriver.chrome.driver", "/usr/local/bin/chromedriver");

        Selenide.open(C.ROOT_ADDRESS);
    }

    @AfterClass(alwaysRun=true)
    public void cleanUp(){
        open(C.ROOT_ADDRESS);
        library()
                .removeFolder(PARALLEL_TEST_FOLDER);
    }

    @Test(dataProvider = "openNewBrowser", threadPoolSize = 10)
    public void parallelLoadTest(String name, String pass) {
        Account testUser = new Account(name, pass);
        loginAs(testUser);
        long testStartTime = System.currentTimeMillis();
        for (int i = 1; i <= 10; i++) {
            long startTime = System.currentTimeMillis();
            navigationMenu()
                    .library()
                    .cd(PARALLEL_TEST_FOLDER);
            executionTime("Open library", name, startTime);
            startTime = System.currentTimeMillis();
            navigationMenu()
                    .runs();
            executionTime("Open active runs", name, startTime);
            startTime = System.currentTimeMillis();
            navigationMenu()
                    .runs()
                    .completedRuns();
            executionTime("Open completed runs", name, startTime);
            startTime = System.currentTimeMillis();
            runsMenu()
                    .completedRuns()
                    .nextPageCompletedRuns();
            executionTime("Open 2nd page completed runs", name, startTime);
            startTime = System.currentTimeMillis();
            runsMenu()
                    .completedRuns()
                    .switchAllPagesCompletedRuns();
            executionTime("Switch between all pages completed runs", name, startTime);
            startTime = System.currentTimeMillis();
            tools()
                    .perform(C.DEFAULT_REGISTRY, C.DEFAULT_GROUP, C.TESTING_TOOL_NAME, ToolTab::runWithCustomSettings);
            executionTime("Open tool", name, startTime);
        }
        executionTime("Summary time ", name, testStartTime);
    }

    private void addUser(Object[] user) {
        navigationMenu()
                .settings()
                .switchToUserManagement()
                .switchToUsers()
                .createIfNotExist(user[0].toString());
    }

    private void executionTime(String action, String user, long startTime) {
        System.out.println(String.format("%s (%s) : execution time %s ms",
                action, user, (System.currentTimeMillis() - startTime)));
    }
}
