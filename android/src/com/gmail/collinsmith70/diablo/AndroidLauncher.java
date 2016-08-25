package com.gmail.collinsmith70.diablo;

import com.google.collinsmith70.diablo.Client;

import android.os.Bundle;

import com.badlogic.gdx.backends.android.AndroidApplication;
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration;

public class AndroidLauncher extends AndroidApplication {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AndroidApplicationConfiguration config = new AndroidApplicationConfiguration();
        config.numSamples = 2;
        config.useAccelerometer = false;
        config.useCompass = false;
        initialize(new Client(1280, 720), config);
    }
}