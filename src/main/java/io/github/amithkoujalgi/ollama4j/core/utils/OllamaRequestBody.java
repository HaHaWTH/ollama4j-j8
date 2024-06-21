package io.github.amithkoujalgi.ollama4j.core.utils;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.JsonProcessingException;

/**
 * Interface to represent a OllamaRequest as HTTP-Request Body.
 */
public interface OllamaRequestBody {

    /**
     * Transforms the OllamaRequest Object to a JSON Object via Jackson.
     *
     * @return JSON representation of a OllamaRequest as String
     */
    @JsonIgnore
    default String toJsonString(){
        try {
            return Utils.getObjectMapper().writeValueAsString(this);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Request not Body convertible.", e);
        }
    }
}
