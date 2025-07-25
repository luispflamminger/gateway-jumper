# SPDX-FileCopyrightText: 2023 Deutsche Telekom AG
#
# SPDX-License-Identifier: Apache-2.0

FROM mtr.devops.telekom.de/tardis-internal/distroless:java-17

EXPOSE 8080

COPY target/*.jar /usr/share/app.jar

CMD ["java", "-jar", "/usr/share/app.jar"]
