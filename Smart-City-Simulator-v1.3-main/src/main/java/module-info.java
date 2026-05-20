module com.trafficsim {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.media;
    requires javafx.graphics;

    opens com.trafficsim to javafx.fxml;
    exports com.trafficsim;
    exports com.trafficsim.controller;
    exports com.trafficsim.model;
    exports com.trafficsim.model.vehicle;
    exports com.trafficsim.model.road;
    exports com.trafficsim.model.intersection;
    exports com.trafficsim.model.driver;
    exports com.trafficsim.service;
    exports com.trafficsim.view.renderer;
    exports com.trafficsim.view.ui;
    exports com.trafficsim.config;
}
