# SauceDemo Autotests

Проект автоматизации тестирования сайта SauceDemo.

## Стек
Java 17, Maven, Selenium WebDriver, TestNG, Page Object Model, Allure, Rest Assured.

## Реализованные авто-тесты
* UI-тест TC-AUTH-001: Успешная авторизация пользователя standard_user. Проверка перехода на inventory.html, заголовка Products и отображения списка товаров. К тесту прилагается скриншот в отчете Allure.
* API-тест API-001: Проверка доступности главной страницы SauceDemo через HTTP GET. Проверка кода ответа 200 и наличия текста Swag Labs.

## Запуск локально
mvn clean test
mvn allure:report

## Запуск в Docker
docker build -t saucedemo-autotests .
docker run --rm -p 8080:8080 saucedemo-autotests

## Веб-интерфейс
После запуска доступен по адресу http://localhost:8080. Позволяет выбирать тесты, запускать их, просматривать логи и открывать отчет Allure.

## CI/CD
Настроен GitHub Actions (.github/workflows/daily_run.yml).
Запуск происходит ежедневно в 06:00 МСК.
Отчет публикуется на GitHub Pages: https://terebok.github.io/demo_JavaSelenium/
Уведомление о результате приходит в виде GitHub Issue.