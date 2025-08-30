package com.example.signinui;

public class WeatherResponse {
    private Main main;
    private Weather[] weather;
    private String name;

    public Main getMain() { return main; }
    public Weather[] getWeather() { return weather; }
    public String getName() { return name; }

    public static class Main {
        private double temp;
        public double getTemp() { return temp; }
    }

    public static class Weather {
        private int id;
        private String main;
        private String description;
        private String icon;

        public int getId() { return id; }
        public String getMain() { return main; }
        public String getDescription() { return description; }
        public String getIcon() { return icon; }
    }
}