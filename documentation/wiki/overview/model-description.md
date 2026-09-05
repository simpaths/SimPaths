# Model Description

SimPaths is an open-source structural dynamic microsimulation framework designed to support experimentation with alternative model assumptions. It is written in Java using the [JAS-mine core](https://github.com/jasmineRepo/JAS-mine-core) and [JAS-mine GUI](https://github.com/jasmineRepo/JAS-mine-gui) libraries.

The SimPaths family includes country-specific implementations for the United Kingdom, Italy, Greece, Hungary and Poland. Their estimation, validation and research readiness are not necessarily the same. This site documents the UK implementation unless a page says otherwise; confirm the status and inputs of another country model with its maintainers before planning an analysis.

SimPaths uses a hierarchy in which people belong to benefit units for fiscal purposes and benefit units belong to households. It projects the population at yearly intervals. The UK documentation is organised around eleven modules:

1. Ageing
2. Education
3. Health
4. Family composition
5. Social care
6. Investment income
7. Labour income
8. Disposable income
9. Consumption
10. Mental health
11. Statistical display

Each module contains one or more processes. For example, ageing includes annual age updates, mortality, child maturation and population alignment. Processes use state from other modules, so model behaviour depends on their ordering as well as on individual equations. See [Simulated Modules](simulated-modules.md) for the overview, individual module pages for current descriptions, and the [UK schedule workbook](https://github.com/simpaths/SimPaths/blob/b223738b9cdf1d814cc3c6f09b04bc4930d3c667/documentation/SimPathsUK_Schedule.xlsx) for the implementation reference.

![Overview of the SimPaths module structure](https://github.com/simpaths/SimPaths/assets/56582427/d4c773a2-b720-4546-bca6-c76d07282dc4)
