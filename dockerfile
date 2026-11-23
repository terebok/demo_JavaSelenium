# Базовый образ: JDK 17 + Maven 3.9.6 (Ubuntu)
FROM maven:3.9.6-eclipse-temurin-17

# Обновление apt, установка утилит, добавление репозитория Google Chrome
RUN apt-get update && apt-get install -y --no-install-recommends \
        wget gnupg unzip ca-certificates \
    && wget -q -O - https://dl.google.com/linux/linux_signing_key.pub \
        | gpg --dearmor -o /usr/share/keyrings/google-chrome.gpg \
    && echo "deb [arch=amd64 signed-by=/usr/share/keyrings/google-chrome.gpg] http://dl.google.com/linux/chrome/deb/ stable main" \
        > /etc/apt/sources.list.d/google-chrome.list \
    && apt-get update && apt-get install -y --no-install-recommends google-chrome-stable \
    && CHROME_VERSION=$(google-chrome --version | awk '{print $3}') \
    && wget -q -O /tmp/chromedriver.zip "https://storage.googleapis.com/chrome-for-testing-public/${CHROME_VERSION}/linux64/chromedriver-linux64.zip" \
    && unzip -q /tmp/chromedriver.zip -d /tmp/ \
    && mv /tmp/chromedriver-linux64/chromedriver /usr/local/bin/chromedriver \
    && chmod +x /usr/local/bin/chromedriver \
    && rm -rf /var/lib/apt/lists/* /tmp/chromedriver*

# Переменные окружения Chrome, ChromeDriver и офлайн-режима Selenium Manager
ENV CHROME_BIN=/usr/bin/google-chrome
ENV CHROMEDRIVER=/usr/local/bin/chromedriver
ENV SE_OFFLINE=true

# Рабочий каталог
WORKDIR /app

# Копирование pom.xml и testng.xml
COPY pom.xml testng.xml ./

# Предзагрузка зависимостей проекта
RUN mvn -B -q dependency:go-offline
# Предзагрузка allure-commandline
RUN mvn -B -q dependency:get -Dartifact=io.qameta.allure:allure-commandline:2.29.0:zip
# Предзагрузка плагина maven-surefire-plugin
RUN mvn -B -q org.apache.maven.plugins:maven-surefire-plugin:3.2.5:help
# Предзагрузка плагина allure-maven
RUN mvn -B -q io.qameta.allure:allure-maven:2.14.0:help
# Предзагрузка провайдера surefire-testng
RUN mvn -B -q dependency:get -Dartifact=org.apache.maven.surefire:surefire-testng:3.2.5
# Предзагрузка артефактов surefire
RUN mvn -B -q dependency:get -Dartifact=org.apache.maven.surefire:surefire-api:3.2.5
RUN mvn -B -q dependency:get -Dartifact=org.apache.maven.surefire:surefire-logger-api:3.2.5
RUN mvn -B -q dependency:get -Dartifact=org.apache.maven.surefire:common-java5:3.2.5
RUN mvn -B -q dependency:get -Dartifact=org.apache.maven.surefire:surefire-shared-utils:3.2.5
# Предзагрузка скина, скачиваемого при генерации Allure-отчёта
RUN mvn -B -q dependency:get -Dartifact=org.apache.maven.skins:maven-default-skin:1.3
# Предзагрузка зависимостей отчёта: slf4j, commons-beanutils, commons-collections4
RUN mvn -B -q dependency:get -Dartifact=org.slf4j:slf4j-api:2.0.7
RUN mvn -B -q dependency:get -Dartifact=org.slf4j:slf4j-api:2.0.10
RUN mvn -B -q dependency:get -Dartifact=org.slf4j:slf4j-api:1.7.21
RUN mvn -B -q dependency:get -Dartifact=commons-beanutils:commons-beanutils:1.9.3
RUN mvn -B -q dependency:get -Dartifact=org.apache.commons:commons-collections4:4.2

# Копирование исходников и тестов
COPY src ./src

# Прогрев сборки без запуска тестов
RUN mvn -B -q -DskipTests clean package

# Прогрев генерации Allure-отчёта для кэширования всех зависимостей отчёта
RUN mkdir -p target/allure-results && mvn -B -q allure:report || true
# Очистка сгенерированных артефактов
RUN rm -rf target

# Копирование веб-интерфейса
COPY WebUi.java ./

# Открытие порта 8080
EXPOSE 8080

# Запуск веб-интерфейса
CMD ["java", "WebUi.java"]