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

package com.epam.pipeline.controller.vo;

import java.util.ArrayList;
import java.util.List;

import com.epam.pipeline.entity.issue.Attachment;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class IssueCommentVO {
    private String text;

    /**
     * Contains parsed markdown from UI
     */
    private String htmlText;
    private List<Attachment> attachments = new ArrayList<>();
}
