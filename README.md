# uz.mirix.security

[![Release](https://jitpack.io/v/M1rix/uz.mirix.security.svg)](https://jitpack.io/#M1rix/uz.mirix.security)

`uz.mirix.security` — библиотека для упрощённого управления безопасностью в Java-приложениях. Она предоставляет удобный интерфейс и набор инструментов для работы с аутентификацией и авторизацией.

## Особенности

- **Легкая интеграция:** Простое подключение в любом Java-проекте.

#### Возможность фильтрации(сокрытия) полей у обьектов
- **RoleBasedFieldFiltering:** Возможность фильтрации полей по текущей роли пользователя.
- **AudienceBasedFieldFiltering:** Возможность фильтрации полей по `JWT.aud` текущего пользователя.

## Установка

### Подключение через JitPack
 1. Добавьте репозиторий JitPack в ваш файл конфигураций
 2. Добавьте зависимость в файл в ваш файл конфигураций
 3. Замените `tag` на версию библиотеки, например `1.0.0`.

#### Maven
Если вы используете Maven, добавьте следующий код в ваш `pom.xml`:
   ```xml
   <repositories>
       <repository>
           <id>jitpack.io</id>
           <url>https://jitpack.io</url>
       </repository>
   </repositories>
    ...
   <dependencies>
       <dependency>
           <groupId>com.github.M1rix</groupId>
           <artifactId>uz.mirix.security</artifactId>
           <version>tag</version>
       </dependency>
   </dependencies>
   ```

#### Gradle
1. Добавьте репозиторий JitPack в ваш файл `build.gradle` (уровень проекта):
   ```groovy
   allprojects {
       repositories {
           ...
           maven { url 'https://jitpack.io' }
       }
   }
   ```

2. Добавьте зависимость в файл `build.gradle` (уровень модуля):
   ```groovy
   dependencies {
       implementation 'com.github.M1rix:uz.mirix.security:tag'
   }
   ```

## Лицензия

Эта библиотека распространяется под лицензией [MIT](LICENSE). Вы можете свободно её использовать в своих проектах.
