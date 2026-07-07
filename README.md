# cloud-itonami-isic-9412

Open Business Blueprint for **ISIC Rev.5 9412**: Activities of
professional membership organizations. This repository publishes an
association actor -- member intake, jurisdiction assessment, ethics-
complaint screening, certification issuance and disciplinary-referral
finalization -- as an OSS business that any qualified operator can
fork, deploy, run, improve and sell.

Built on this workspace's
[`langgraph-clj`](https://github.com/com-junkawasaki/langgraph-clj)
StateGraph runtime (portable `.cljc`, supervised superstep loop,
interrupts, Datomic/in-mem checkpoints) -- the same actor pattern as
every prior actor in this fleet
([`cloud-itonami-isic-6511`](https://github.com/cloud-itonami/cloud-itonami-isic-6511),
[`6512`](https://github.com/cloud-itonami/cloud-itonami-isic-6512),
[`6621`](https://github.com/cloud-itonami/cloud-itonami-isic-6621),
[`6622`](https://github.com/cloud-itonami/cloud-itonami-isic-6622),
[`6629`](https://github.com/cloud-itonami/cloud-itonami-isic-6629),
[`6520`](https://github.com/cloud-itonami/cloud-itonami-isic-6520),
[`6530`](https://github.com/cloud-itonami/cloud-itonami-isic-6530),
[`6820`](https://github.com/cloud-itonami/cloud-itonami-isic-6820),
[`6612`](https://github.com/cloud-itonami/cloud-itonami-isic-6612),
[`6492`](https://github.com/cloud-itonami/cloud-itonami-isic-6492),
[`6920`](https://github.com/cloud-itonami/cloud-itonami-isic-6920),
[`6611`](https://github.com/cloud-itonami/cloud-itonami-isic-6611),
[`7120`](https://github.com/cloud-itonami/cloud-itonami-isic-7120),
[`8620`](https://github.com/cloud-itonami/cloud-itonami-isic-8620),
[`8530`](https://github.com/cloud-itonami/cloud-itonami-isic-8530),
[`9200`](https://github.com/cloud-itonami/cloud-itonami-isic-9200),
[`7500`](https://github.com/cloud-itonami/cloud-itonami-isic-7500),
[`9603`](https://github.com/cloud-itonami/cloud-itonami-isic-9603),
[`9521`](https://github.com/cloud-itonami/cloud-itonami-isic-9521),
[`9321`](https://github.com/cloud-itonami/cloud-itonami-isic-9321),
[`8730`](https://github.com/cloud-itonami/cloud-itonami-isic-8730),
[`9102`](https://github.com/cloud-itonami/cloud-itonami-isic-9102),
[`9103`](https://github.com/cloud-itonami/cloud-itonami-isic-9103),
[`9602`](https://github.com/cloud-itonami/cloud-itonami-isic-9602),
[`9000`](https://github.com/cloud-itonami/cloud-itonami-isic-9000),
[`8890`](https://github.com/cloud-itonami/cloud-itonami-isic-8890),
[`8610`](https://github.com/cloud-itonami/cloud-itonami-isic-8610),
[`9311`](https://github.com/cloud-itonami/cloud-itonami-isic-9311),
[`8510`](https://github.com/cloud-itonami/cloud-itonami-isic-8510)) --
the FIRST membership/professional-body-governance vertical (ISIC
division 94) in this fleet. Here it is **AssocOps-LLM ⊣ Association
Governance Governor**.

> **Why an actor layer at all?** An LLM is great at drafting a member-
> intake summary, normalizing records, and checking whether a
> member's own completed continuing-education hours have actually
> reached the association's own required minimum -- but it has **no
> notion of which jurisdiction's professional-body-governance
> requirements are official, no license to issue a real certification
> or finalize a real disciplinary referral, and no way to know on its
> own whether an ethics complaint against the member has actually
> stayed unresolved**. Letting it issue a certification or finalize a
> disciplinary referral directly invites fabricated jurisdiction
> citations, a certification issued to a member short of the required
> continuing-education hours, and an unresolved ethics complaint being
> quietly overlooked -- and liability, and reputational risk, for
> whoever runs it. This project seals the AssocOps-LLM into a single
> node and wraps it with an independent **Association Governance
> Governor**, a human **approval workflow**, and an immutable **audit
> ledger**.

## Scope: what this actor does and does not do

This actor covers member intake through jurisdiction assessment,
ethics-complaint screening, certification issuance and disciplinary-
referral finalization. It does **not**, by itself, hold any
accreditation required to operate a professional membership
organization in a given jurisdiction, and it does not claim to. It
also does **not** model a full standards-setting/continuing-education-
curriculum engine -- no subject-matter examination workflow, no
accreditation-body-specific scoring rubric, no full case-management
system for disciplinary hearings themselves (see `association.facts`'s
own docstring for the honest simplification this makes: a general
professional-body-governance catalog, not a profession-by-profession
survey of every credentialing standard). Whoever deploys and operates
a live instance (a licensed/accredited association operator) supplies
any jurisdiction-specific accreditation, the real standards-setting
and disciplinary expertise and the real membership-management-system
integrations, and bears that jurisdiction's liability -- the software
supplies the governed, spec-cited, audited execution scaffold so that
operator does not have to build the compliance layer from scratch for
every new market.

### Actuation

**Issuing a real certification or finalizing a real disciplinary
referral is never autonomous, at any phase, by construction.** Two
independent layers enforce this (`association.governor`'s
`:actuation/issue-certification`/`:actuation/finalize-disciplinary-
referral` high-stakes gate and `association.phase`'s phase table,
which never puts `:certification/issue`/`:discipline/finalize` in any
phase's `:auto` set) -- see `association.phase`'s docstring and
`test/association/phase_test.clj`'s `certification-issue-never-auto-
at-any-phase`/`discipline-finalize-never-auto-at-any-phase`. The actor
may draft, check and recommend; a human association officer is always
the one who actually issues a certification or finalizes a
disciplinary referral. Like `6512`/`6622`/`6520`/`6530`/`6820`/`6920`/
`6611`/`8530`/`9200`/`9521`/`8730`/`9102`/`9103`/`8890`/`8610`/`8510`,
this actor has TWO actuation events.

## The core contract

```
member intake + jurisdiction facts (association.facts, spec-cited)
        |
        v
   ┌──────────────┐   proposal      ┌───────────────────────┐
   │ AssocOps-    │ ─────────────▶ │ Association                   │  (independent system)
   │ LLM (sealed) │  + citations    │ Governance Governor:          │
   └──────────────┘                 │ spec-basis · evidence-       │
                             commit ◀────┼──────────▶ hold │ incomplete ·
                                 │             │           │ continuing-education-
                           record + ledger  escalate ─▶ human   insufficient (MINIMUM-
                                             (ALWAYS for         threshold, non-temporal) ·
                                              :certification/         complaint-unresolved
                                              issue /                 (unconditional) ·
                                              :discipline/finalize)     already-certified/-disciplined
```

**The AssocOps-LLM never issues a certification or finalizes a
disciplinary referral the Association Governance Governor would
reject, and never does so without a human sign-off.** Hard violations
(fabricated jurisdiction requirements; unsupported evidence;
insufficient continuing-education hours; an unresolved ethics
complaint; a double certification-issuance or disciplinary-referral-
finalization) force **hold** and *cannot* be approved past; a clean
certification/disciplinary-referral proposal still always routes to a
human.

## Run

```bash
clojure -M:dev:run     # walk one clean lifecycle (certification issuance + disciplinary-referral finalization) + five HARD-hold cases through the actor
clojure -M:dev:test    # governor contract · phase invariants · store parity · registry conformance · facts coverage
clojure -M:lint        # clj-kondo (errors fail; CI mirrors this)
```

## Robotics premise

All cloud-itonami verticals are designed on the premise that a **robot
performs the physical domain work**. Here a document-courier robot
handles physical credential-mailing fulfillment where used, under the
actor, gated by the independent **Association Governance Governor**.
The governor never dispatches hardware itself; `:high`/`:safety-
critical` actions require human sign-off.

## Open business

This repository is not only source code. It is a public, forkable
business model:

| Layer | What is open |
|---|---|
| OSS core | Actor runtime, Association Governance Governor, certification-issuance + disciplinary-referral-finalization draft records, audit ledger |
| Business blueprint | Customer, offer, pricing, unit economics, sales motion |
| Operator playbook | How to fork, license, deploy and support the service in a jurisdiction |
| Trust controls | Governance, security reporting, actuation invariant, audit requirements |

See [`docs/business-model.md`](docs/business-model.md) and
[`docs/operator-guide.md`](docs/operator-guide.md) to start this as an
open business on itonami.cloud, and
[`docs/adr/0001-architecture.md`](docs/adr/0001-architecture.md) for the
full architecture and decision record.

## Capability layer

This blueprint resolves its technology stack via
[`kotoba-lang/industry`](https://github.com/kotoba-lang/industry) (ISIC
`9412`). Like `6920`/`7120`/`8620`/`8530`/`9200`/`7500`/`9603`/`9521`/
`9321`/`8730`/`9102`/`9103`/`9602`/`9000`/`8890`/`8610`/`9311`/`8510`,
this vertical's member records are practice-specific rather than a
shared cross-operator data contract, so `association.*` runs on the
generic identity/forms/dmn/bpmn/audit-ledger stack only -- no bespoke
domain capability lib to reference at all.

## Layout

| File | Role |
|---|---|
| `src/association/store.cljc` | **Store** protocol -- `MemStore` ‖ `DatomicStore` (`langchain.db`) + append-only audit ledger + separate certification-issuance/disciplinary-referral-finalization history. No dynamically-filed sub-record -- both actuation ops act directly on a pre-seeded member, and the double-certification/double-discipline guards check dedicated `:certified?`/`:disciplined?` booleans rather than a `:status` value |
| `src/association/registry.cljc` | Certification-issuance + disciplinary-referral-finalization draft records, plus `continuing-education-hours-insufficient?` -- the FIRST non-temporal instance of this fleet's MINIMUM-threshold sufficiency family (`veterinary`/`funeral`/`hospital` established the first three, all temporal) |
| `src/association/facts.cljc` | Per-jurisdiction professional-body-governance catalog with an official spec-basis citation per entry, honest coverage reporting |
| `src/association/assocopsllm.cljc` | **AssocOps-LLM Advisor** -- `mock-advisor` ‖ `llm-advisor`; intake/assessment/complaint-screening/certification-issuance/disciplinary-referral-finalization proposals |
| `src/association/governor.cljc` | **Association Governance Governor** -- 4 HARD checks (spec-basis · evidence-incomplete · continuing-education-insufficient, pure ground-truth MINIMUM-threshold recompute · complaint-unresolved, unconditional evaluation, the TWENTIETH grounding of this discipline and FIRST specifically for the professional-ethics/conduct-complaint concept) + already-certified/already-disciplined guards + 1 soft (confidence/actuation gate) |
| `src/association/phase.cljc` | **Phase 0→3** -- read-only → assisted intake → assisted assess → supervised (both certification and disciplinary-referral finalization always human; member intake is the ONLY auto-eligible op, no direct capital risk) |
| `src/association/operation.cljc` | **OperationActor** -- langgraph-clj StateGraph |
| `src/association/sim.cljc` | demo driver |
| `test/association/*_test.clj` | governor contract · phase invariants · store parity · registry conformance · facts coverage |

## Business-process coverage (honest)

This actor covers member intake through jurisdiction assessment,
ethics-complaint screening, certification issuance and disciplinary-
referral finalization -- the core governed lifecycle this blueprint's
own `docs/business-model.md` names as its Offer:

| Covered | Not covered (out of scope for this R0) |
|---|---|
| Member intake + per-jurisdiction professional-body-governance checklisting, HARD-gated on an official spec-basis citation (`:member/intake`/`:jurisdiction/assess`) | A full standards-setting/continuing-education-curriculum engine (subject-matter examination workflows, accreditation-body-specific scoring rubrics -- see `association.facts`'s docstring) |
| Ethics-complaint screening, evaluated unconditionally so the screening op itself can HARD-hold on its own finding (`:complaint/screen`) | Real membership-management-system integration, dues-billing workflows |
| Certification issuance, HARD-gated on full evidence and continuing-education sufficiency, plus a double-issuance guard (`:certification/issue`) | Full case-management workflows for disciplinary hearings themselves |
| Disciplinary-referral finalization, HARD-gated on full evidence and a double-finalization guard (`:discipline/finalize`) | |
| Immutable audit ledger for every intake/assessment/screening/certification/referral decision | |

Extending coverage is additive: add the next gate (e.g. a dues-in-
arrears check) as its own governed op with its own HARD checks and
tests, following the SAME "an independent governor re-verifies against
the actor's own records before any real-world act" pattern this repo's
flagship op already establishes.

## Jurisdiction coverage (honest)

`association.facts/coverage` reports how many requested jurisdictions
actually have an official spec-basis in `association.facts/catalog` --
currently 4 seeded (JPN, USA, GBR, DEU) out of ~194 jurisdictions
worldwide. This is a starting catalog to prove the governor contract
end-to-end, not a claim of global coverage. Adding a jurisdiction is
additive: one map entry in `association.facts/catalog`, citing a real
official source -- never fabricate a jurisdiction's requirements to make
coverage look bigger.

## Maturity

`:implemented` -- `AssocOps-LLM` + `Association Governance Governor`
run as real, tested code (see `Run` above), promoted from the
originally-published `:blueprint`-tier scaffold, modeled closely on
the twenty-nine prior actors' architecture. See `docs/adr/0001-
architecture.md` for the history and design.

## License

Code and implementation templates are AGPL-3.0-or-later.
