package com.saucedemo.tests;

// mvn clean test allure:serve
import com.saucedemo.pages.InventoryPage;
import com.saucedemo.pages.LoginPage;
import com.saucedemo.utils.DriverFactory;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Story;
import org.openqa.selenium.WebDriver;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

@Epic("SauceDemo")
@Feature("Авторизация")
public class LoginTest {

    private WebDriver driver;
    private LoginPage loginPage;

    @BeforeMethod
    public void setUp() {
        driver = DriverFactory.createDriver();
        loginPage = new LoginPage(driver);
    }

    @AfterMethod
    public void tearDown() {
        DriverFactory.quitDriver(driver);
    }

    @Test(description = "TC-AUTH-001: Успешная авторизация пользователя standard_user")
    @Story("TC-AUTH-001")
    @Description("Открыть сайт, ввести standard_user/secret_sauce, нажать Login, проверить переход на inventory.html и заголовок Products")
    public void testSuccessfulLogin() {
        InventoryPage inventoryPage = loginPage
                .open("https://www.saucedemo.com")
                .enterUsername("standard_user")
                .enterPassword("secret_sauce")
                .clickLogin();

        Assert.assertEquals(inventoryPage.getCurrentUrl(), "https://www.saucedemo.com/inventory.html");
        Assert.assertEquals(inventoryPage.getPageTitle(), "Products");
        Assert.assertTrue(inventoryPage.isInventoryListDisplayed());
    }
}