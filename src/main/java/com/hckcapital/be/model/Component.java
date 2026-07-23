package com.hckcapital.be.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Generic content blob referenced by Card.components / lineFormatComponent / flexFormatHtml.
 * One Card owns three of these: the round-trippable editor tree ("flexCard"), the
 * spec-compliant LINE Flex Message JSON actually sent to LINE ("line"), and a rendered
 * HTML preview ("html").
 */
@Data
@Document(collection = "components")
public class Component {

    @Id
    private String id;

    private String componentID;

    private String componentType;

    private String content;
}
