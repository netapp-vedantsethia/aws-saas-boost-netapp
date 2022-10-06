/**
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.amazon.aws.partners.saasfactory.saasboost.workflow;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.amazon.aws.partners.saasfactory.saasboost.clients.AwsClientBuilderFactory;
import com.amazon.aws.partners.saasfactory.saasboost.clients.MockAwsClientBuilderFactory;
import com.amazon.aws.partners.saasfactory.saasboost.model.Environment;

public class UpdateWorkflowTest {

    private static final Environment testEnvironment = Environment.builder()
            .name("ENV")
            .accountId("123456789012")
            .build();
    private static final Path workingDir = Paths.get("");
    
    private UpdateWorkflow updateWorkflow;
    private AwsClientBuilderFactory clientBuilderFactory;

    @Before
    public void setup() {
        clientBuilderFactory = new MockAwsClientBuilderFactory();
        updateWorkflow = new UpdateWorkflow(workingDir, testEnvironment, clientBuilderFactory);
    }

    @After
    public void cleanup() {
        for (UpdateAction action : UpdateAction.values()) {
            action.resetTargets();
        }
    }

    @Test
    public void testGetCloudFormationParameterMap() throws Exception {
        // The input map represents the existing CloudFormation parameter values.
        // These will either be the template defaults, or they will be the parameter
        // values read from a created stack with the describeStacks call.
        // We'll pretend that the RequiredStringParameter parameter is newly added
        // to the template on disk so the user should be prompted for a value
        Map<String, String> input = new LinkedHashMap<>();
        input.put("DefaultStringParameter", "foobar");
        input.put("NumericParameter", "1"); // Let's pretend that we overwrote the default the first time around

        // Fill up standard input with a response for the Keyboard class
        System.setIn(new ByteArrayInputStream(("keyboard input" + System.lineSeparator()).getBytes(StandardCharsets.UTF_8)));

        Path cloudFormationTemplate = Path.of(this.getClass().getClassLoader().getResource("template.yaml").toURI());
        Map<String, String> actual = UpdateWorkflow.getCloudFormationParameterMap(cloudFormationTemplate, input);

        Map<String, String> expected = new LinkedHashMap<>();
        expected.put("RequiredStringParameter", "keyboard input");
        expected.put("DefaultStringParameter", "foobar");
        expected.put("NumericParameter", "1");

        assertEquals("Template has 3 parameters", expected.size(), actual.size());
        for (Map.Entry<String, String> entry : expected.entrySet()) {
            assertEquals(entry.getKey() + " equals " + entry.getValue(), entry.getValue(), actual.get(entry.getKey()));
        }
    }

    @Test
    public void testUpdateActionsFromPaths_basic() {
        Set<UpdateAction> expectedActions = EnumSet.of(UpdateAction.CLIENT, UpdateAction.FUNCTIONS);
        List<Path> changedPaths = List.of(
            Path.of("client/src/App.js"),
            Path.of("functions/onboarding-app-stack-listener/pom.xml"));
        Collection<UpdateAction> actualActions = updateWorkflow.getUpdateActionsFromPaths(changedPaths);
        assertEquals(expectedActions, actualActions);
        actualActions.forEach(action -> {
            if (action == UpdateAction.FUNCTIONS) {
                assertEquals(1, action.getTargets().size());
                assertEquals(1, UpdateAction.FUNCTIONS.getTargets().size());
                assertTrue(action.getTargets().contains("onboarding-app-stack-listener"));
            }
        });
    }

    @Test
    public void testUpdateActionsFromPaths_unrecognizedPath() {
        Set<UpdateAction> expectedActions = EnumSet.of(UpdateAction.FUNCTIONS, UpdateAction.SERVICES);
        List<Path> unrecognizedPaths = List.of(
            Path.of("abc/unrecognized/path.java"),
            Path.of("services/new-service/src/main/java/MyService.java"),
            Path.of("services/really-new-service/src/main/java/MyService.java"),
            Path.of("functions/new-function/pom.xml"));
        Collection<UpdateAction> actualActions = updateWorkflow.getUpdateActionsFromPaths(unrecognizedPaths);
        assertEquals(expectedActions, actualActions);
        actualActions.forEach(action -> {
            if (action == UpdateAction.FUNCTIONS) {
                assertEquals(1, action.getTargets().size());
                assertTrue(action.getTargets().contains("new-function"));
            }
            if (action == UpdateAction.SERVICES) {
                assertEquals(2, action.getTargets().size());
                assertTrue(action.getTargets().contains("new-service"));
                assertTrue(action.getTargets().contains("really-new-service"));
            }
        });
    }

    @Test
    public void testUpdateActionsFromPaths_customResourcesPath() {
        Set<UpdateAction> expectedActions = EnumSet.of(UpdateAction.CUSTOM_RESOURCES, UpdateAction.RESOURCES);
        List<Path> changedPaths = List.of(
            Path.of("resources/saas-boost.yaml"),
            Path.of("resources/new-cfn-template.yaml"),
            Path.of("resources/custom-resources/app-services-ecr-macro/pom.xml"),
            Path.of("resources/custom-resources/new-resource/pom.xml"));
        Collection<UpdateAction> actualActions = updateWorkflow.getUpdateActionsFromPaths(changedPaths);
        assertEquals(expectedActions, actualActions);
        actualActions.forEach(action -> {
            if (action == UpdateAction.RESOURCES) {
                assertEquals(2, action.getTargets().size());
                assertTrue(action.getTargets().contains("saas-boost.yaml"));
                assertTrue(action.getTargets().contains("new-cfn-template.yaml"));
            }
            if (action == UpdateAction.CUSTOM_RESOURCES) {
                assertEquals(2, action.getTargets().size());
                assertTrue(action.getTargets().contains("app-services-ecr-macro"));
                assertTrue(action.getTargets().contains("new-resource"));
            }
        });
    }
}