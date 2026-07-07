(ns association.registry
  "Pure-function certification-issuance + disciplinary-referral-
  finalization record construction -- an append-only association
  book-of-record draft.

  Like every sibling actor's registry, there is no single
  international check-digit standard for a certification-issuance or
  disciplinary-referral reference number -- every association/
  jurisdiction assigns its own reference format. This namespace does
  NOT invent one; it builds a jurisdiction-scoped sequence number and
  validates the record's required fields, the same honest, non-
  fabricating discipline `association.facts` uses.

  `continuing-education-hours-insufficient?` is the FIRST NON-TEMPORAL
  instance of this fleet's MINIMUM-threshold sufficiency family
  (`veterinary.registry/withdrawal-period-insufficient?`, `funeral.
  registry/waiting-period-elapsed?` and `hospital.registry/
  observation-period-elapsed?` established the first three, all
  TEMPORAL -- a minimum time interval must elapse). This check
  generalizes the family to a non-temporal numeric ground truth: a
  member's own completed continuing-education hours must reach the
  association's own recorded minimum requirement, the same way
  `facility.registry/occupancy-exceeds-capacity?` and `school.
  registry/class-size-exceeds-maximum?` generalized the MAXIMUM-
  ceiling family from elapsed time to non-temporal numeric ground
  truths.

  This namespace is pure data + pure functions -- no I/O, no network
  call to any real membership-management system. It builds the RECORD
  an association would keep, not the act of issuing the certification
  or finalizing the disciplinary referral itself (that is
  `association.operation`'s `:certification/issue`/`:discipline/
  finalize`, always human-gated -- see README `Actuation`)."
  (:require [clojure.string :as str]))

(defn- unsigned-certificate
  "Every certificate this actor produces is UNSIGNED -- signature is the
  association's own act, not this actor's. See README `Actuation`."
  [kind subject record-id]
  {"@context" ["https://www.w3.org/ns/credentials/v2"]
   "type" ["VerifiableCredential" kind]
   "credentialSubject" {"id" subject "record" record-id}
   "proof" nil
   "issued_by_registry" false
   "status" "draft-unsigned"})

(defn- zero-pad [n w]
  (let [s (str n)]
    (str (apply str (repeat (max 0 (- w (count s))) "0")) s)))

(defn continuing-education-hours-insufficient?
  "Does `member`'s own `:ce-hours-completed` fall short of the
  association's own recorded `:ce-hours-required` minimum? A pure
  ground-truth check against the member's own permanent fields -- no
  upstream comparison needed."
  [{:keys [ce-hours-completed ce-hours-required]}]
  (and (number? ce-hours-completed) (number? ce-hours-required)
       (< ce-hours-completed ce-hours-required)))

(defn register-certification-issuance
  "Validate + construct the CERTIFICATION-ISSUANCE registration DRAFT
  -- the association's own legal act of issuing a real professional
  certification to a member. Pure function -- does not touch any real
  membership-management system; it builds the RECORD an association
  would keep. `association.governor` independently re-verifies the
  member's own continuing-education sufficiency and unresolved-
  complaint status, and blocks a double-issuance to the same member,
  before this is ever allowed to commit."
  [member-id jurisdiction sequence]
  (when-not (and member-id (not= member-id ""))
    (throw (ex-info "certification-issuance: member_id required" {})))
  (when-not (and jurisdiction (not= jurisdiction ""))
    (throw (ex-info "certification-issuance: jurisdiction required" {})))
  (when (< sequence 0)
    (throw (ex-info "certification-issuance: sequence must be >= 0" {})))
  (let [certification-number (str (str/upper-case jurisdiction) "-CRT-" (zero-pad sequence 6))
        record {"record_id" certification-number
                "kind" "certification-issuance-draft"
                "member_id" member-id
                "jurisdiction" jurisdiction
                "immutable" true}]
    {"record" record "certification_number" certification-number
     "certificate" (unsigned-certificate "CertificationIssuance" certification-number certification-number)}))

(defn register-disciplinary-referral-finalization
  "Validate + construct the DISCIPLINARY-REFERRAL-FINALIZATION
  registration DRAFT -- the association's own legal act of finalizing
  a real disciplinary referral against a member. Pure function -- does
  not touch any real membership-management system; it builds the
  RECORD an association would keep. `association.governor`
  independently re-verifies the jurisdiction's evidence checklist, and
  blocks a double-finalization of the same member's referral, before
  this is ever allowed to commit."
  [member-id jurisdiction sequence]
  (when-not (and member-id (not= member-id ""))
    (throw (ex-info "disciplinary-referral-finalization: member_id required" {})))
  (when-not (and jurisdiction (not= jurisdiction ""))
    (throw (ex-info "disciplinary-referral-finalization: jurisdiction required" {})))
  (when (< sequence 0)
    (throw (ex-info "disciplinary-referral-finalization: sequence must be >= 0" {})))
  (let [referral-number (str (str/upper-case jurisdiction) "-DSC-" (zero-pad sequence 6))
        record {"record_id" referral-number
                "kind" "disciplinary-referral-finalization-draft"
                "member_id" member-id
                "jurisdiction" jurisdiction
                "immutable" true}]
    {"record" record "referral_number" referral-number
     "certificate" (unsigned-certificate "DisciplinaryReferralFinalization" referral-number referral-number)}))

(defn append [history result]
  (conj (vec history) (get result "record")))
