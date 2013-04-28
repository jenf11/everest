package org.apache.felix.ipojo.everest.servlet;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Json utilities.
 */
public class JsonUtils {

    public static String CONTENT_TYPE = "application/json";

    private static Json default_singleton = new Json();

    public  static Json get() {
        return default_singleton;
    }

    public static  class Json {
        private final ObjectMapper mapper;

        public Json() {
            mapper = new ObjectMapper();
        }

        public Json(ObjectMapper mapper) {
            this.mapper = mapper;
        }

        public ObjectMapper getMapper() {
            return mapper;
        }

        /**
         * Convert a JsonNode to its string representation.
         */
        public String stringify(JsonNode json) {
            return json.toString();
        }

        /**
         * Parse a String representing a json, and return it as a JsonNode.
         */
        public JsonNode parse(String src) {
            try {
                return getMapper().readValue(src, JsonNode.class);
            } catch(Throwable t) {
                throw new RuntimeException(t);
            }
        }

        /**
         * Convert an object to JsonNode.
         *
         * @param data Value to convert in Json.
         */
        public JsonNode toJson(final Object data) {
            try {
                return getMapper().valueToTree(data);
            } catch(Exception e) {
                throw new RuntimeException(e);
            }
        }

        /**
         * Converts a JsonNode to a Java value
         *
         * @param json Json value to convert.
         * @param clazz Expected Java value type.
         */
        public <A> A fromJson(JsonNode json, Class<A> clazz) {
            try {
                return getMapper().treeToValue(json, clazz);
            } catch(Exception e) {
                throw new RuntimeException(e);
            }
        }

        /**
         * Creates a new empty ObjectNode.
         */
        public ObjectNode newObject() {
            return getMapper().createObjectNode();
        }
    }





}