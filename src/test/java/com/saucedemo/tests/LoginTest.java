package com.saucedemo.tests;

import com.saucedemo.pages.InventoryPage;
import com.saucedemo.pages.LoginPage;
import com.saucedemo.utils.DriverFactory;
// Добавлено: импорт класса Allure для прикрепления файлов
import io.qameta.allure.Allure;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Story;
// Добавлено: импорт OutputType для получения скриншота в виде байтов
import org.openqa.selenium.OutputType;
// Добавлено: импорт интерфейса TakesScreenshot для создания скриншотов
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

// Добавлено: импорт ByteArrayInputStream для передачи скриншота в Allure
import java.io.ByteArrayInputStream;

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

    // Изменено: добавлен параметр alwaysRun = true, чтобы скриншот делался даже при падении теста
    @AfterMethod(alwaysRun = true)
    public void tearDown() {
        // Добавлено: блок try-catch для безопасного создания скриншота
        try {
            // Добавлено: проверка, поддерживает ли драйвер создание скриншотов
            if (driver instanceof TakesScreenshot) {
                // Добавлено: получение скриншота в виде массива байт
                byte[] screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
                // Добавлено: прикрепление скриншота к отчету Allure с именем "Скриншот"
                Allure.addAttachment("Скриншот", "image/png", new ByteArrayInputStream(screenshot), ".png");
            }
        } catch (Exception e) {
            // Добавлено: вывод ошибки в консоль, если скриншот не удался
            e.printStackTrace();
        }
        // Существующий код закрытия драйвера
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