---
title: Social care
---

<div class="module-detail" markdown>

# Social care

<p class="module-detail__lead">The UK social-care module projects the receipt and provision of care for people in need because of poor health or advanced age. It distinguishes formal and informal care, providers' time costs and recipients' formal-care costs.</p>

## Receipt of social care

The care-receipt equations apply from a minimum age threshold. This reflects the higher prevalence of care among older people and the more detailed information available for that population.

### Older people

For people above the age threshold, the model determines whether care is needed and whether it is received using separate probit equations. These condition on individual and household characteristics and previous care circumstances.

For a person projected to receive care, a multinomial logit distinguishes among informal care only, formal and informal care, and formal care only. The equation also includes education, partnership status, age band and a lagged dependent variable.

Separate equations project hours of informal and formal care, conditional on receipt of each type. They are estimated on an inverse-hyperbolic-sine scale; the simulation adds a residual draw and converts the result back to hours. Hours of formal care are converted to a cost using the year-specific mean hourly wage of social-care workers.

### Younger people

The care-receipt process does not currently project care for people below the age threshold. Care provision has a separate minimum age and includes younger adults.

## Provision of informal care

The model projects whether people provide informal care and how many hours they provide. Formal-sector providers are represented through the hourly costs used to value formal care.

Starting-population datasets generally do not record the wider social links implied by informal care, apart from links between partners. In particular, links between adult children and parents, and wider informal-care networks, are often absent. The provision model is designed to work within those data constraints.

Separate probit equations for single and partnered people determine whether they provide informal care. Conditional on provision, ordered-logit equations assign a care-hours category, again distinguishing single and partnered people. Each category has a representative number of weekly hours.

The UK receipt and provision processes are implemented in [`Person.evaluateSocialCareReceipt()` and `Person.evaluateSocialCareProvision()`](https://github.com/simpaths/SimPaths/blob/develop/src/main/java/simpaths/model/Person.java).

</div>
