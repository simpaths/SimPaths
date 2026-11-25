SimPaths UK - validation graphs: simulation configuration
=========================================================

The graphs in this folder were produced from the simulation run of 4 Nov 2026.


RUN SET-UP
  Country                 UK
  Start year              2011
  End year                2023 (inclusive)
  Population size         50,000
  Number of runs          5 (run folders _0 to _4)
  Random seeds            606, 607, 608, 609, 610 (one per run, +1 each run)
  Bootstrap               ON - regression coefficients are re-drawn in each run
  Base price year         2015 (financial variables in real 2015 prices)


ALIGNMENT
  Population              ON
  Cohabitation            ON
  Fertility               off
  Education               off
  In school               off
  Employment              off

OTHER MODEL OPTIONS
  Intertemporal optimisation      off
  Mortality projected             on
  Social care / formal childcare  on
  COVID-19 labour market module   off
  Time trend fixed from           2023 (also for monetary processes)
  Regression stochastic component on (not fixed across years)
  Tax donor pool averaging        on
  Tax donor uprating by wage      off (uprated by price inflation)


VALIDATION SETTINGS (00_master.do)
  max_n_runs              4 (i.e. runs 0-4)
  Years plotted           2013-2023 (min_sim_year / max_sim_year); the simulation starts in 2011


GRAPH FOLDERS
  SimPaths output is compared with UKHLS, except consumption (LCFS) and
  unsecured debt (WAS).

  File names: _ts_ = time series 2013-2023; _dist / _hist / _cat_YYYY = one
  graph per year; _both / _male / _female / _gender = sex split; numbers such
  as 16_65 = age range; _bu = benefit-unit level, _ind = individual level.

  economic_activity/     Activity status shares (employed, student, not
                         employed, retired) by age group, sex and partnership
  education/             Education level shares by age group; leaving education
  hours_worked/          Weekly hours: time series, yearly histograms and
                         hours-category shares, by sex
  wages/                 Hourly wages: time series and yearly distributions
  income/
    gross_income/        Benefit-unit and individual gross income
    gross_labour_income/ Benefit-unit and individual gross labour income
    capital_income/      Capital income, incl. share with none
    pension_income/      Pension income, age 65+, incl. share with none
    disposable_income/   Benefit-unit disposable income
    equivalised_disposable_income/  Equivalised disposable income
    income_shares/       Income composition (levels and shares), <=65 / 66+ / all
                         (income folders: time series plus yearly
                         distributions, overall and by weekly hours band)
  inequality/            Gini, P90/P10, P90/P50, net transfers by age group
  poverty/               At-risk-of-poverty rate, ages 18-65 and 18+
  partnership/           Partnership status by age group and by children
  children/              Number of children, newborns, young children (18-65)
  health/                Self-rated health, SF-12 mental (MCS) and physical
                         (PCS) scores
  disability/            Disability share, by sex and age group
  social_care/           Care need, receipt (65+), provision (16+), care type,
                         care hours and formal care expenditure
  consumption/           Consumption-to-income ratio: overall, by age, by
                         income quintile (wealth module)
  unsecured_debt/        Unsecured debt holding, amounts, transitions
                         (wealth module)
  correlations/          Empty - no graphs produced


