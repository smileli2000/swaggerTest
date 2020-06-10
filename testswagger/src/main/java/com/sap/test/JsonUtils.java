package com.sap.test;


import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import com.google.json.JsonSanitizer;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JsonUtils {

    /**
     * Utility classes should not have public constructors
     */
    private JsonUtils() {

    }

    public static final String PATH_REGEX = "\\[@(-?\\d+)\\]";

    public static boolean containsKey(String key, JsonElement obj) {
        if (obj.isJsonObject()) {
            JsonObject jsonObj = obj.getAsJsonObject();
            Iterator<Map.Entry<String, JsonElement>> iterator = jsonObj.entrySet()
                    .iterator();
            while (iterator.hasNext()) {
                Map.Entry<String, JsonElement> item = iterator.next();
                if (item.getKey().equalsIgnoreCase(key)) {
                    return true;
                }
                JsonElement value = item.getValue();
                containsKey(key, value);
            }
        }
        return false;
    }

//    /**
//     * Traverse JSON object by given path, e.g. "/a/b" to get "42" from
//     * JsonObject { "a" : { "b" : 42 } }
//     *
//     * @param path split by "/", support to get a specified element of an
//     *             JsonArray, e.g. "/a/b/[@1]" will get "42" from { "a" : { "b" : [41, 42] } }
//     * @param obj  JsonObject
//     * @return found JsonElement, if not found, JsonNull will return
//     * @throws IndexOutOfBoundsException
//     */
////    public static JsonElement getByPath(String path, JsonElement obj) {
////        return getByPath(Arrays.asList(path.split("/")).stream().filter(e -> e.length() != 0).collect(Collectors.toList()).iterator(), obj);
////    }

    private static JsonElement getByPath(Iterator<String> segments, JsonElement obj) {
        if (!segments.hasNext()) {
            return obj;
        } else if (!obj.isJsonObject() && !obj.isJsonArray()) {
            return JsonNull.INSTANCE;
        } else {
            String path = segments.next();

            final Pattern regex = Pattern.compile(PATH_REGEX);
            Matcher matcher = regex.matcher(path);

            if (matcher.find() && obj.isJsonArray()) {
                JsonArray array = obj.getAsJsonArray();
                Integer offset = getArrayOffset(matcher, array);
                return getByPath(segments, array.get(offset));
            } else if (obj.isJsonObject()) {
                JsonObject jsonObject = obj.getAsJsonObject();

                if (!jsonObject.has(path)) {
                    return JsonNull.INSTANCE;
                } else {
                    JsonElement element = jsonObject.get(path);
                    return getByPath(segments, element);
                }
            } else {
                return JsonNull.INSTANCE;
            }
        }
    }

    private static Integer getArrayOffset(Matcher matcher, JsonArray array) {
        Integer offset = Integer.valueOf(matcher.group(1));
        int total = array.size();

        // -1 means the last element
        if (offset < 0) {
            offset = total + offset;
        }

        if (offset > total || offset < 0) {
            throw new IndexOutOfBoundsException();
        }
        return offset;
    }

    public static String generateJsonStringFromBean(Object object) {
        return getGson().toJson(object);
    }

    public static JsonElement generateJsonElementFromBean(Object object) {
        return getGson().toJsonTree(object);
    }

    public static <T> T generateBeanFromJsonElement(JsonElement jsonElement, Class<T> targetClass) {
        return getGson().fromJson(jsonElement, targetClass);
    }

    public static <T> T generateBeanFromJson(String json, Class<T> targetClass) {
        String sanitizedJson = JsonSanitizer.sanitize(json);
        return getGson().fromJson(sanitizedJson, targetClass);
    }

    //public static <T> List<T> generateBeanListFromJson(String json, Class<T> targetClass) {
    //    String sanitizedJson = JsonSanitizer.sanitize(json);
    //    return getGson().fromJson(sanitizedJson, new TypeToken<List<T>>(){}.getType());
    //}

    //    /**
//     * Parse json string to json object
//     *
//     * @param jsonString json string
//     * @return json object
//     */
    public static JsonObject generateJsonObjectFromJsonString(String jsonString) {
        return generateBeanFromJson(jsonString, JsonObject.class);
    }

//    /**
//     * Parse string to json element
//     *
//     * @param jsonString json string
//     * @return json element object
//     */

    public static JsonElement generateJsonElementFromString(String jsonString) {
        return generateBeanFromJson(jsonString, JsonElement.class);
    }

    public static String generateJsonStringFromJsonElement(JsonElement jsonElement) {
        return jsonElement.toString();
    }

    /**
     * Generate JsonArray from value list
     *
     * @param valueList value list
     * @return Json array
     */
    public static JsonArray generateJsonArrayFromList(List<String> valueList) {
        JsonElement enumJsonArray = (getGson()).toJsonTree(valueList, new TypeToken<List<String>>() {
        }.getType());
        return enumJsonArray.getAsJsonArray();
    }

    public static String getValueAsString(JsonObject jsonObject, String propertyName) {
        JsonElement jsonElement = jsonObject.get(propertyName);
        return isNull(jsonElement) ? null : jsonElement.getAsString();
    }

    public static JsonArray getValueAsJsonArray(JsonObject jsonObject, String propertyName) {
        JsonElement jsonElement = jsonObject.get(propertyName);
        return isNull(jsonElement) ? null : jsonElement.getAsJsonArray();
    }

    public static JsonObject getValueAsJsonObject(JsonObject jsonObject, String propertyName) {
        JsonElement jsonElement = jsonObject.get(propertyName);
        return isNull(jsonElement) ? null : jsonElement.getAsJsonObject();
    }

    @SuppressWarnings("unchecked")
    public static <T> List<T> getElements(JsonArray jsonArray, Class<T> elementType) {
        List<T> result = new ArrayList();
        for (int i = 0; i < jsonArray.size(); i++) {
            JsonElement jsonElement = jsonArray.get(i);
            if (elementType == String.class) {
                result.add((T) jsonElement.getAsString());
            } else if (elementType == JsonObject.class) {
                result.add((T) jsonElement.getAsJsonObject());
            }
        }
        return result;
    }

//    public static JsonElement fromIPropertyValue(IPropertyValue value) {
//        if (value.getInternalValue() == null) {
//            return JsonNull.INSTANCE;
//        } else if (value instanceof BooleanValue) {
//            return new JsonPrimitive(((BooleanValue)value).getInternalValue());
//        } else if (value instanceof StringValue) {
//            return new JsonPrimitive(((StringValue)value).getInternalValue());
//        } else if (value instanceof IntegerValue) {
//            return new JsonPrimitive(((IntegerValue)value).getInternalValue());
//        } else if (value instanceof DecimalValue) {
//            return new JsonPrimitive(((DecimalValue)value).getInternalValue());
//        } else if (value instanceof DateValue) {
//            return new JsonPrimitive(((DateValue)value).getInternalValue().toString());
//        } else if (value instanceof TimestampValue) {
//            return new JsonPrimitive(((TimestampValue)value).getInternalValue().toString());
//        } else if (value instanceof UUIDValue) {
//            return new JsonPrimitive(((UUIDValue)value).getInternalValue().toString());
//        } else if (value instanceof NullValue) {
//            return JsonNull.INSTANCE;
//        } else if (value instanceof ObjectValue) {
//            return fromObjectValue((ObjectValue)value);
//        } else if (value instanceof ListValue) {
//            return fromListValue((ListValue)value);
//        }
//        return JsonNull.INSTANCE;
//    }
//
//    private static JsonElement fromObjectValue(ObjectValue objectValue) {
//        Map<String, IPropertyValue> map = objectValue.getInternalValue();
//        JsonObject json = new JsonObject();
//        for (Map.Entry<String, IPropertyValue> entry : map.entrySet()) {
//            String key = entry.getKey();
//            IPropertyValue value = entry.getValue();
//            json.add(key, fromIPropertyValue(value));
//        }
//        return json;
//    }
//
//    private static JsonElement fromListValue(ListValue listValue) {
//        JsonArray array = new JsonArray();
//        List<IPropertyValue> valueList = listValue.getInternalValue();
//        for (IPropertyValue value : valueList) {
//            array.add(fromIPropertyValue(value));
//        }
//        return array;
//    }

    public static boolean isNull(JsonElement jsonElement) {
        return jsonElement == null || jsonElement.isJsonNull();
    }

    protected static Gson getGson() {
        //return new GsonBuilder().registerTypeAdapter(Instant.class, new InstantAdapter()).registerTypeAdapter(Duration.class, new DurationAdapter()).create();
        return new Gson();
    }
}