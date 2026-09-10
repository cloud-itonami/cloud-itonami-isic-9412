(ns association.governor
  "Association Governance Governor -- the independent compliance layer
  that earns the AssocOps-LLM the right to commit. The LLM has no
  notion of jurisdictional professional-body governance law, whether a
  member's own continuing-education hours have actually reached the
  association's own required minimum, whether an ethics complaint
  against the member has actually stayed unresolved, or when an act
  stops being a draft and becomes a real-world certification issuance
  or disciplinary-referral finalization, so this MUST be a separate
  system able to *reject* a proposal and fall back to HOLD -- the
  association analog of `cloud-itonami-isic-6512`'s CasualtyGovernor.

  Six checks, in priority order, ALL HARD violations: a human approver
  CANNOT override them (you don't get to approve your way past a
  fabricated jurisdiction spec-basis, incomplete evidence, insufficient
  continuing-education hours, an unresolved ethics complaint, or a
  double certification-issuance/disciplinary-referral-finalization).
  The confidence/actuation gate is SOFT: it asks a human to look (low
  confidence / actuation), and the human may approve -- but see
  `association.phase`: for `:stake :actuation/issue-certification`/
  `:actuation/finalize-disciplinary-referral` (a real member-record
  act) NO phase ever allows auto-commit either. Two independent layers
  agree that actuation is always a human call.

    1. Spec-basis                  -- did the jurisdiction proposal cite
                                       an OFFICIAL source (`association.
                                       facts`), or invent one?
    2. Evidence incomplete         -- for `:certification/issue`/
                                       `:discipline/finalize`, has the
                                       jurisdiction actually been
                                       assessed with a full member-
                                       registration/standards-
                                       conformance/complaints-procedure/
                                       governance-disclosure evidence
                                       checklist on file?
    3. Continuing-education
       hours insufficient            -- for `:certification/issue`,
                                       INDEPENDENTLY recompute whether
                                       the member's own `:ce-hours-
                                       completed` reaches the
                                       association's own recorded
                                       `:ce-hours-required` (`association.
                                       registry/continuing-education-
                                       hours-insufficient?`) -- needs no
                                       proposal inspection or stored-
                                       verdict lookup at all. The FIRST
                                       NON-TEMPORAL instance of this
                                       fleet's MINIMUM-threshold
                                       sufficiency family (`veterinary.
                                       governor/withdrawal-period-
                                       insufficient-violations`/`funeral.
                                       governor/waiting-period-not-
                                       elapsed-violations`/`hospital.
                                       governor/observation-period-
                                       insufficient-violations`
                                       established the first three, all
                                       TEMPORAL).
    4. Complaint unresolved         -- reported by THIS proposal itself
                                       (a `:complaint/screen` that just
                                       found an unresolved ethics
                                       complaint), or already on file
                                       for the member (`:complaint/
                                       screen`/`:certification/issue`).
                                       Evaluated UNCONDITIONALLY (not
                                       scoped to a specific op), the
                                       SAME discipline `casualty.
                                       governor/sanctions-violations`/
                                       ...(nineteen prior siblings)...
                                       established -- the TWENTIETH
                                       distinct application of this
                                       exact discipline, and the FIRST
                                       specifically for the
                                       professional-ethics/conduct-
                                       complaint concept. Like the
                                       nine most recent siblings'
                                       equivalent checks, this is
                                       exercised in tests/demo via
                                       `:complaint/screen` DIRECTLY, not
                                       via an actuation op against an
                                       unscreened member -- see this
                                       ns's own test suite.
    5. Confidence floor / actuation
       gate                          -- LLM confidence below threshold,
                                       OR the op is `:certification/
                                       issue`/`:discipline/finalize`
                                       (REAL member-record acts) ->
                                       escalate.

  Two more guards, double-certification/double-discipline prevention,
  are enforced but NOT listed as numbered HARD checks above because
  they need no upstream comparison at all -- `already-certified-
  violations`/`already-disciplined-violations` refuse to issue a
  certification/finalize a disciplinary referral for the SAME member
  twice, off dedicated `:certified?`/`:disciplined?` facts (never a
  `:status` value) -- the SAME 'check a dedicated boolean, not status'
  discipline every prior sibling governor's guards establish, informed
  by `cloud-itonami-isic-6492`'s status-lifecycle bug
  (ADR-2607071320)."
  (:require [association.facts :as facts]
            [association.registry :as registry]
            [association.store :as store]))

(def confidence-floor 0.6)

(def high-stakes
  "Stakes grave enough to always require a human, even when clean.
  Issuing a real certification and finalizing a real disciplinary
  referral are the two real-world actuation events this actor
  performs -- a two-member set, matching every prior dual-actuation
  sibling's shape."
  #{:actuation/issue-certification :actuation/finalize-disciplinary-referral})

;; ----------------------------- checks -----------------------------

(defn- spec-basis-violations
  "A `:jurisdiction/assess` (or `:certification/issue`/`:discipline/
  finalize`) proposal with no spec-basis citation is a HARD violation
  -- never invent a jurisdiction's professional-body governance
  requirements."
  [{:keys [op]} proposal]
  (when (contains? #{:jurisdiction/assess :certification/issue :discipline/finalize} op)
    (let [value (:value proposal)]
      (when (or (empty? (:cites proposal))
                (and (contains? value :spec-basis) (nil? (:spec-basis value))))
        [{:rule :no-spec-basis
          :detail "公式spec-basisの引用が無い提案は法域要件として扱えない"}]))))

(defn- evidence-incomplete-violations
  "For `:certification/issue`/`:discipline/finalize`, the
  jurisdiction's required member-registration/standards-conformance/
  complaints-procedure/governance-disclosure evidence must actually be
  satisfied -- do not trust the advisor's self-reported confidence
  alone."
  [{:keys [op subject]} st]
  (when (contains? #{:certification/issue :discipline/finalize} op)
    (let [m (store/member st subject)
          assessment (store/assessment-of st subject)]
      (when-not (and assessment
                     (facts/required-evidence-satisfied?
                      (:jurisdiction m) (:checklist assessment)))
        [{:rule :evidence-incomplete
          :detail "法域の必要書類(会員登録記録/資格認定基準適合証明/苦情懲戒処理手続き文書/組織運営情報開示書等)が充足していない状態での提案"}]))))

(defn- continuing-education-insufficient-violations
  "For `:certification/issue`, INDEPENDENTLY recompute whether the
  member's own continuing-education hours reach the association's own
  recorded minimum via `association.registry/continuing-education-
  hours-insufficient?` -- needs no proposal inspection or stored-
  verdict lookup at all, since its input is a permanent ground-truth
  field already on the member."
  [{:keys [op subject]} st]
  (when (= op :certification/issue)
    (let [m (store/member st subject)]
      (when (registry/continuing-education-hours-insufficient? m)
        [{:rule :continuing-education-insufficient
          :detail (str subject " の継続教育履修時間(" (:ce-hours-completed m)
                      ")が必要時間(" (:ce-hours-required m) ")に満たない")}]))))

(defn- complaint-unresolved-violations
  "An unresolved ethics/conduct complaint -- reported by THIS proposal
  (e.g. a `:complaint/screen` that itself just found an unresolved
  complaint), or already on file in the store for the member
  (`:complaint/screen`/`:certification/issue`) -- is a HARD,
  un-overridable hold. Evaluated UNCONDITIONALLY (not scoped to a
  specific op) so the screening op itself can HARD-hold on its own
  finding."
  [{:keys [op subject]} proposal st]
  (let [hit-in-proposal? (= :unresolved (get-in proposal [:value :verdict]))
        member-id (when (contains? #{:complaint/screen :certification/issue} op) subject)
        hit-on-file? (and member-id (= :unresolved (:verdict (store/complaint-of st member-id))))]
    (when (or hit-in-proposal? hit-on-file?)
      [{:rule :complaint-unresolved
        :detail "未解決の倫理・行動規範に関する苦情がある状態での資格認定発行提案は進められない"}])))

(defn- already-certified-violations
  "For `:certification/issue`, refuses to issue a certification to the
  SAME member twice, off a dedicated `:certified?` fact (never a
  `:status` value)."
  [{:keys [op subject]} st]
  (when (= op :certification/issue)
    (when (store/member-already-certified? st subject)
      [{:rule :already-certified
        :detail (str subject " は既に資格認定発行済み")}])))

(defn- already-disciplined-violations
  "For `:discipline/finalize`, refuses to finalize a disciplinary
  referral for the SAME member twice, off a dedicated `:disciplined?`
  fact (never a `:status` value)."
  [{:keys [op subject]} st]
  (when (= op :discipline/finalize)
    (when (store/member-already-disciplined? st subject)
      [{:rule :already-disciplined
        :detail (str subject " は既に懲戒付託確定済み")}])))

(defn check
  "Censors an AssocOps-LLM proposal against the governor rules.
  Returns {:ok? bool :violations [..] :confidence c :escalate? bool
  :high-stakes? bool :hard? bool}."
  [request _context proposal st]
  (let [hard (into []
                   (concat (spec-basis-violations request proposal)
                           (evidence-incomplete-violations request st)
                           (continuing-education-insufficient-violations request st)
                           (complaint-unresolved-violations request proposal st)
                           (already-certified-violations request st)
                           (already-disciplined-violations request st)))
        conf (:confidence proposal 0.0)
        low? (< conf confidence-floor)
        stakes? (boolean (high-stakes (:stake proposal)))
        hard? (boolean (seq hard))]
    {:ok?          (and (not hard?) (not low?) (not stakes?))
     :violations   hard
     :confidence   conf
     :hard?        hard?
     :escalate?    (and (not hard?) (or low? stakes?))
     :high-stakes? stakes?}))

(defn hold-fact
  "The audit fact written when a proposal is rejected (HOLD)."
  [request context verdict]
  {:t          :governor-hold
   :op         (:op request)
   :actor      (:actor-id context)
   :subject    (:subject request)
   :disposition :hold
   :basis      (mapv :rule (:violations verdict))
   :violations (:violations verdict)
   :confidence (:confidence verdict)})
