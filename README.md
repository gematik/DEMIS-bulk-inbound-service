<div style="text-align:right"><img src="https://raw.githubusercontent.com/gematik/gematik.github.io/master/Gematik_Logo_Flag_With_Background.png" width="250" height="47" alt="gematik GmbH Logo"/> 
 </div> 
<br/> 

# Bulk-Inbound-Service

[![Quality Gate Status](https://sonar.prod.ccs.gematik.solutions/api/project_badges/measure?project=de.gematik.demis%3Abulk-inbound&metric=alert_status&token=sqb_3a851d4bfb337e2972daee045af439a929fa492d)](https://sonar.prod.ccs.gematik.solutions/dashboard?id=de.gematik.demis%3Abulk-inbound)
[![Vulnerabilities](https://sonar.prod.ccs.gematik.solutions/api/project_badges/measure?project=de.gematik.demis%3Abulk-inbound&metric=vulnerabilities&token=sqb_3a851d4bfb337e2972daee045af439a929fa492d)](https://sonar.prod.ccs.gematik.solutions/dashboard?id=de.gematik.demis%3Abulk-inbound)
[![Bugs](https://sonar.prod.ccs.gematik.solutions/api/project_badges/measure?project=de.gematik.demis%3Abulk-inbound&metric=bugs&token=sqb_3a851d4bfb337e2972daee045af439a929fa492d)](https://sonar.prod.ccs.gematik.solutions/dashboard?id=de.gematik.demis%3Abulk-inbound)
[![Code Smells](https://sonar.prod.ccs.gematik.solutions/api/project_badges/measure?project=de.gematik.demis%3Abulk-inbound&metric=code_smells&token=sqb_3a851d4bfb337e2972daee045af439a929fa492d)](https://sonar.prod.ccs.gematik.solutions/dashboard?id=de.gematik.demis%3Abulk-inbound)
[![Lines of Code](https://sonar.prod.ccs.gematik.solutions/api/project_badges/measure?project=de.gematik.demis%3Abulk-inbound&metric=ncloc&token=sqb_3a851d4bfb337e2972daee045af439a929fa492d)](https://sonar.prod.ccs.gematik.solutions/dashboard?id=de.gematik.demis%3Abulk-inbound)
[![Coverage](https://sonar.prod.ccs.gematik.solutions/api/project_badges/measure?project=de.gematik.demis%3Abulk-inbound&metric=coverage&token=sqb_3a851d4bfb337e2972daee045af439a929fa492d)](https://sonar.prod.ccs.gematik.solutions/dashboard?id=de.gematik.demis%3Abulk-inbound)

<details>
  <summary>Table of Contents</summary>
  <ol>
    <li>
      <a href="#about-the-project">About The Project</a>
      <ul>
        <li><a href="#release-notes">Release Notes</a></li>
      </ul>
    </li>
    <li>
      <a href="#getting-started">Getting Started</a>
      <ul>
        <li><a href="#prerequisites">Prerequisites</a></li>
        <li><a href="#installation">Installation</a></li>
      </ul>
    </li>
    <li><a href="#usage">Usage</a></li>
    <li><a href="#security-policy">Security Policy</a></li>
    <li><a href="#contributing">Contributing</a></li>
    <li><a href="#license">License</a></li>
    <li><a href="#contact">Contact</a></li>
  </ol>
</details>

## About The Project

Here should go information about the project.

### Release Notes

See [ReleaseNotes.md](./ReleaseNotes.md) for all information regarding the (newest) releases.

## Getting Started

### Prerequisites

The Project requires Java 21 and Maven 3.8+.

### Installation

The Project can be built with the following command:

```sh
mvn clean install
```

The Docker Image associated to the service can be built with the extra profile `docker`:

```sh
mvn clean install -Pdocker
```

## Usage

The application can be executed from a JAR file or a Docker Image:

```sh
# As JAR Application
java -jar target/bulk-inbound-service.jar
# As Docker Image
docker run --rm -it -p 8080:8080 bulk-inbound-service:latest
```

It can also be deployed on Kubernetes by using the Helm Chart defined in the folder `deployment/helm/bulk-inbound-service`:

```ssh
helm install bulk-inbound-service ./deployment/helm/bulk-inbound-service
```

### Continuous Integration and Delivery

The project contains Jenkins Pipelines to perform automatic build and scanning (`ci.jenkinsfile`) and release (based on retagging of the given Git Tag, `release.jenkinsfile`).
Please adjust the variable values defined at the beginning of the pipelines!

For both the pipelines, you need to create a first initial Release Version in JIRA, so it can be retrieved from Jenkins with the Jenkins Shared Library functions.

**BEWARE**: The Release Pipeline requires a manual configuration of the parameters over the Jenkins UI, defining a JIRA Release Version plugin and naming it `JIRA_RELEASE_VERSION`.
The Information such as Project Key and Regular Expression depends on the project and must be correctly configured.

### Endpoints

Define here the available endpoints exposed by the service

## Security Policy
If you want to see the security policy, please check our [SECURITY.md](.github/SECURITY.md).

## Contributing
If you want to contribute, please check our [CONTRIBUTING.md](.github/CONTRIBUTING.md).

## License

Copyright 2023-2025 gematik GmbH

EUROPEAN UNION PUBLIC LICENCE v. 1.2

EUPL © the European Union 2007, 2016

See the [LICENSE](./LICENSE.md) for the specific language governing permissions and limitations under the License

## Additional Notes and Disclaimer from gematik GmbH

1. Copyright notice: Each published work result is accompanied by an explicit statement of the license conditions for use. These are regularly typical conditions in connection with open source or free software. Programs described/provided/linked here are free software, unless otherwise stated.
2. Permission notice: Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions::
    1. The copyright notice (Item 1) and the permission notice (Item 2) shall be included in all copies or substantial portions of the Software.
    2. The software is provided "as is" without warranty of any kind, either express or implied, including, but not limited to, the warranties of fitness for a particular purpose, merchantability, and/or non-infringement. The authors or copyright holders shall not be liable in any manner whatsoever for any damages or other claims arising from, out of or in connection with the software or the use or other dealings with the software, whether in an action of contract, tort, or otherwise.
    3. The software is the result of research and development activities, therefore not necessarily quality assured and without the character of a liable product. For this reason, gematik does not provide any support or other user assistance (unless otherwise stated in individual cases and without justification of a legal obligation). Furthermore, there is no claim to further development and adaptation of the results to a more current state of the art.
3. Gematik may remove published results temporarily or permanently from the place of publication at any time without prior notice or justification.
4. Please note: Parts of this code may have been generated using AI-supported technology.’ Please take this into account, especially when troubleshooting, for security analyses and possible adjustments.

## Contact
E-Mail to [DEMIS Entwicklung](mailto:demis-entwicklung@gematik.de?subject=[GitHub]%20Bulk-Inbound-Service)