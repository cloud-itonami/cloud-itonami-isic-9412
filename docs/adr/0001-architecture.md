# ADR-0001: cloud-itonami-isic-9412 -- AssocOps-LLM as a contained intelligence node

- Status: Accepted (2026-07-08)
- Related: `cloud-itonami-isic-6511`/`6512`/`6621`/`6622`/`6629`/`6520`/
  `6530`/`6820`/`6612`/`6492`/`6920`/`6611`/`7120`/`8620`/`8530`/`9200`/
  `7500`/`9603`/`9521`/`9321`/`8730`/`9102`/`9103`/`9602`/`9000`/`8890`/
  `8610`/`9311`/`8510` ADR-0001s (the pattern this ADR ports);
  ADR-2607071250/ADR-2607071320/ADR-2607071351/ADR-2607071618/
  ADR-2607071640/ADR-2607071654/ADR-2607071717/ADR-2607071732/
  ADR-2607071752/ADR-2607071819/ADR-2607071849/ADR-2607071922/
  ADR-2607072715/ADR-2607072730/ADR-2607072745/ADR-2607072800/
  ADR-2607072815/ADR-2607072830/ADR-2607072845/ADR-2607072900/
  ADR-2607072915 (`6612`/`6492`/`6920`/`6611`/`7120`/`8620`/`8530`/
  `9200`/`7500`/`9603`/`9521`/`9321`/`8730`/`9102`/`9103`/`9602`/
  `9000`/`8890`/`8610`/`9311`/`8510`, the twenty-one verticals built
  outside ADR-2607032000's original insurance/real-estate batch --
  this is the twenty-second)
- Context: Continuing the standing "pick a new ISIC blueprint
  vertical" direction past `8510`, this ADR deepens `cloud-itonami-
  isic-9412` (activities of professional membership organizations)
  from `:blueprint` to `:implemented`, the thirty-sixth actor in this
  fleet -- the FIRST membership/professional-body-governance vertical
  (ISIC division 94) built in this fleet.

## Problem

An association's certification-issuance/disciplinary-referral-
finalization workflow bundles several distinct concerns under one
governed workflow:

1. **Jurisdiction professional-body-governance correctness** -- an
   official spec-basis citation from a real regulator (内閣府公益認定
   等委員会/the ANSI National Accreditation Board/the Professional
   Standards Authority/the Kammern der Länder), never fabricated.
2. **Continuing-education sufficiency** -- does a member's own
   completed continuing-education hours reach the association's own
   recorded minimum requirement? The FIRST NON-TEMPORAL instance of
   this fleet's MINIMUM-threshold sufficiency family (`veterinary.
   registry/withdrawal-period-insufficient?`, `funeral.registry/
   waiting-period-elapsed?` and `hospital.registry/observation-period-
   elapsed?` established the first three, all TEMPORAL -- a minimum
   time interval must elapse).
3. **Ethics-complaint resolution verification** -- has an ethics/
   conduct complaint against the member actually stayed unresolved
   before a new certification is issued? The association-specific
   application of the unconditional-evaluation screening discipline
   this fleet's `casualty.governor/sanctions-violations` originally
   established -- a TWENTIETH distinct grounding overall, and the
   FIRST specifically for the professional-ethics/conduct-complaint
   concept.
4. **Real, high-stakes actuation, twice** -- issuing a real
   certification and finalizing a real disciplinary referral are two
   independently-gated real-world acts on the SAME entity (a
   professional member).

An LLM has no authority or grounding for any of these. The design
problem is therefore not "run a professional membership organization
with an LLM" but "seal the LLM inside a trust boundary and layer
evidence-sufficiency, continuing-education verification, ethics-
complaint-resolution verification, audit and human-approval on top of
it, while structurally fixing both real actuation events as human-
only."

## Decision

### 1. AssocOps-LLM is sealed into the bottom node; it never issues a certification or finalizes a referral directly

`association.assocopsllm` returns exactly five kinds of proposal:
intake normalization, jurisdiction professional-body-governance
checklist, ethics-complaint screening, certification-issuance draft,
and disciplinary-referral-finalization draft. No proposal writes the
SSoT or commits a real certification/referral directly.

### 2. OperationActor = langgraph-clj StateGraph, 1 run = 1 association operation

`association.operation/build` is the SAME StateGraph shape as every
sibling actor's operation namespace, copied verbatim.

### 3. `continuing-education-hours-insufficient?` is the FIRST non-temporal instance of the MINIMUM-threshold sufficiency family

`veterinary.registry/withdrawal-period-insufficient?` established the
FIRST check in this fleet's MINIMUM-threshold sufficiency family
(gated on a `:food-producing?` type tag), `funeral.registry/waiting-
period-elapsed?` the SECOND (applied unconditionally), and `hospital.
registry/observation-period-elapsed?` the THIRD (also unconditional)
-- all three TEMPORAL (a minimum time interval must elapse).
`continuing-education-hours-insufficient?` is the FOURTH instance
overall and the FIRST NON-TEMPORAL one, generalizing the family the
same way `facility.registry/occupancy-exceeds-capacity?` and `school.
registry/class-size-exceeds-maximum?` generalized the MAXIMUM-ceiling
family from elapsed time to non-temporal numeric ground truths: a
member's own completed continuing-education hours must reach the
association's own recorded minimum requirement.

### 4. Complaint-unresolved screening reuses the unconditional-evaluation discipline for a twentieth distinct grounding, and a first specifically for this concept

`complaint-unresolved-violations` reuses `casualty.governor/
sanctions-violations`'s fix (evaluated unconditionally, not scoped to
a specific op, so the screening op itself can HARD-hold on its own
finding) for `:complaint/screen` AND `:certification/issue` -- the
TWENTIETH distinct application of this exact discipline in this fleet
overall, and the FIRST specifically for the professional-ethics/
conduct-complaint concept. This check deliberately does NOT gate
`:discipline/finalize` -- an unresolved complaint is the natural
PRECURSOR to a disciplinary referral, not a reason to block finalizing
one, so extending the same "presence of a problem blocks the
actuation" shape to that op would misrepresent the domain (unlike
`hospital`'s credential-not-current check, which legitimately gates
BOTH of that actor's actuations because a lapsed clinician license
taints either act equally).

### 5. The unconditional-evaluation check is tested via the SCREENING op directly, per the lesson already recorded by `parksafety` and nine later siblings

`complaint-unresolved-is-held-and-unoverridable` calls `:complaint/
screen` directly against `member-4` (an unresolved complaint), NOT
`:certification/issue` against an unscreened member -- because a
failing screen is itself a HARD hold whose payload never persists to
the store, so the actuation op alone could never discover the bad
ground-truth flag through this check family without the screening op
having actually been run first. This build applied that lesson
PROACTIVELY for a tenth consecutive vertical (after `eldercare`,
`museum`, `conservation`, `salon`, `entertainment`, `casework`,
`hospital`, `facility` and `school`), further reinforcing that lessons
recorded in this fleet's ADRs transfer forward reliably.

### 6. Dual actuation, matching `6512`/`6622`/`6520`/`6530`/`6820`/`6920`/`6611`/`8530`/`9200`/`9521`/`8730`/`9102`/`9103`/`8890`/`8610`/`8510`'s shape

`association.governor`'s `high-stakes` set has exactly two members
(`:actuation/issue-certification`, `:actuation/finalize-disciplinary-
referral`), each acting on the SAME member entity, each with its OWN
history collection (`certification-history`/`discipline-history`),
sequence counter and dedicated double-actuation-guard boolean.

### 7. Double-certification/double-discipline guards check dedicated booleans, not `:status`

`already-certified-violations`/`already-disciplined-violations` check
`:certified?`/`:disciplined?`, dedicated booleans set once and never
cleared, rather than a `:status` value that could legitimately advance
past a checked state (the exact trap `cloud-itonami-isic-6492`'s
ADR-0001 documents in detail, explicitly avoided BY DESIGN in every
sibling actor's equivalent guard since). This actor's `:status` never
needs to encode "has this actuation already happened" at all -- a
deliberate architectural choice applied here for a twentieth
consecutive time.

### 8. No bespoke capability lib

Like `6920`/`7120`/`8620`/`8530`/`9200`/`7500`/`9603`/`9521`/`9321`/
`8730`/`9102`/`9103`/`9602`/`9000`/`8890`/`8610`/`9311`/`8510`, and
unlike most other actors in this fleet, this vertical's member records
are practice-specific rather than a shared cross-operator data
contract -- `association.*` runs on the generic identity/forms/dmn/
bpmn/audit-ledger stack only, per the blueprint's own explicit
statement.

## Consequences

- (+) Professional-membership-organization governance gets the same
  governed, auditable-actor treatment as the twenty-nine prior actors,
  and this fleet now has a TWENTY-SECOND concrete precedent for
  extending past ADR-2607032000's original scope, and its FIRST
  membership/professional-body-governance vertical (ISIC division 94).
- (+) `continuing-education-hours-insufficient?` is a genuine
  structural contribution: the first non-temporal instance of the
  MINIMUM-threshold sufficiency family, further validating that
  family's generality beyond elapsed-time comparisons the same way the
  MAXIMUM-ceiling family was previously generalized.
- (+) `complaint-unresolved-violations` is a genuine domain-modeling
  contribution: the first time this fleet's unconditional-evaluation
  discipline has been scoped to gate only ONE of two actuations by
  deliberate domain reasoning, rather than reflexively applying to
  both.
- (+) The actuation invariant (governor + phase, two layers) is
  regression-tested by `test/association/phase_test.clj`'s
  `certification-issue-never-auto-at-any-phase`/`discipline-finalize-
  never-auto-at-any-phase`.
- (+) `MemStore` ‖ `DatomicStore` parity is proven by `test/
  association/store_contract_test.clj`, the same `:db-api`-driven swap
  pattern every sibling actor uses.
- (+) The complaint-unresolved test/demo correctly applied the
  established SCREENING-op-directly pattern for a tenth consecutive
  vertical -- further evidence that lessons recorded in this fleet's
  ADRs continue to transfer forward reliably.
- (-) This R0 seeds only 4 jurisdictions (JPN, USA, GBR, DEU) with an
  official spec-basis, out of ~194 worldwide; `association.facts/
  coverage` reports this honestly rather than claiming broader
  coverage.
- (-) `continuing-education-hours-insufficient?` models only a single
  completed-vs-required-hours comparison, not a full standards-
  setting/continuing-education-curriculum engine (subject-matter
  examination workflows, accreditation-body-specific scoring rubrics
  are out of scope -- see `association.facts`'s own docstring); real
  membership-management-system integration and full disciplinary-
  hearing case-management workflows are all out of scope for this OSS
  actor -- each operator's responsibility (see README's coverage
  table).
- 36 tests / 173 assertions, lint clean.

## Alternatives considered

| Option | Verdict | Reason |
|---|---|---|
| Add this as an addendum to any prior post-batch ADR | ❌ | All twenty-one of those ADRs' titles and scopes are explicitly `cloud-itonami-isic-6612`/`6492`/`6920`/`6611`/`7120`/`8620`/`8530`/`9200`/`7500`/`9603`/`9521`/`9321`/`8730`/`9102`/`9103`/`9602`/`9000`/`8890`/`8610`/`9311`/`8510`; this is also this fleet's first membership/professional-body-governance vertical, with no prior sibling sharing even the same broad ISIC division |
| Keep `cloud-itonami-isic-9412` at `:blueprint` only | ❌ | The standing direction continues past `8510`; professional-membership-organization governance is a natural next domain, opening this fleet's first membership-sector coverage |
| Scope `complaint-unresolved-violations` to gate BOTH actuations (matching `hospital`'s credential-not-current shape exactly) | ❌ | An unresolved complaint is the natural precursor to a disciplinary referral, not a disqualifying condition for finalizing one -- blindly copying the "gate both actuations" shape here would block the very act meant to resolve the complaint, misrepresenting the domain; the honest choice is to gate only `:certification/issue` |
| Model `continuing-education-hours-insufficient?` as a new, unrelated check family | ❌ | The actual comparison shape (a minimum numeric threshold that must be reached) is identical to the established MINIMUM-threshold family; honestly framing this as its first NON-TEMPORAL instance keeps the fleet's check-family taxonomy accurate |
| Test `complaint-unresolved-violations` via an actuation op against an unscreened member (the shape `parksafety`'s ORIGINAL, buggy test used) | ❌ | Already proven wrong by `parksafety`'s own ADR-2607071922 Decision 5 and reconfirmed by nine later siblings' ADR-0001s -- a failing screen never persists its payload to the store, so the actuation op alone cannot discover the bad ground-truth flag through this check family; this build tested the SCREENING op directly from the start |
| Reference a capability lib (e.g. a hypothetical `kotoba-lang/association`) for consistency with most prior actors | ❌ | The blueprint itself explicitly states this vertical's records are practice-specific, not a shared cross-operator contract -- inventing a capability lib reference where the blueprint says none exists would misrepresent the domain, the same reasoning established by every "no bespoke capability lib" sibling's ADR |

## References

- ADR-2607071250/ADR-2607071320/ADR-2607071351/ADR-2607071618/
  ADR-2607071640/ADR-2607071654/ADR-2607071717/ADR-2607071732/
  ADR-2607071752/ADR-2607071819/ADR-2607071849/ADR-2607071922/
  ADR-2607072715/ADR-2607072730/ADR-2607072745/ADR-2607072800/
  ADR-2607072815/ADR-2607072830/ADR-2607072845/ADR-2607072900/
  ADR-2607072915 (`6612`/`6492`/`6920`/`6611`/`7120`/`8620`/`8530`/
  `9200`/`7500`/`9603`/`9521`/`9321`/`8730`/`9102`/`9103`/`9602`/
  `9000`/`8890`/`8610`/`9311`/`8510`, first twenty-one post-batch
  verticals)
- ADR-2607032000 (original insurance/real-estate batch, Addenda 1-7)
- `cloud-itonami-isic-9412/docs/adr/0001-architecture.md` (this ADR)
- `kotoba-lang/industry` `resources/kotoba/industry/registry.edn`
  (fleet-wide maturity registry)
