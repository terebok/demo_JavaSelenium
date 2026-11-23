package com.saucedemo.tests;

import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Story;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.testng.Assert;
import org.testng.annotations.Test;

import static io.restassured.RestAssured.given;

@Epic("SauceDemo")
@Feature("API")
public class ApiTest {

    @Test(description = "Проверка доступности главной страницы SauceDemo через HTTP GET")
    @Story("API-001")
    public void testMainPageAvailability() {
        RestAssured.baseURI = "https://www.saucedemo.com";

        Response response = given()
                .when()
                .get("/")
                .then()
                .extract()
                .response();

        Assert.assertEquals(response.getStatusCode(), 200);
        Assert.assertTrue(response.getBody().asString().contains("Swag Labs"));
    }
}