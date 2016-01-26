package com.gmail.collinsmith70.util.serializer;

import com.gmail.collinsmith70.util.StringSerializer;

import java.util.Locale;

public enum LocaleStringSerializer implements StringSerializer<Locale> {
INSTANCE;

@Override
public String serialize(Locale obj) {
    return obj.toLanguageTag();
}

@Override
public Locale deserialize(String obj) {
    return java.util.Locale.forLanguageTag(obj);
}

}