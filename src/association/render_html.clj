(ns association.render-html
  "Build-time HTML renderer for `docs/samples/operator-console.html`.

  Closes flagship checklist item 2 (com-junkawasaki/root ADR-2607189300,
  Wave5 rollout ledger): this repo previously had NO demo page and no
  generator at all. This namespace drives the REAL actor stack
  (`association.operation` -> `association.governor` -> `association.
  store`) through a scenario adapted from this repo's own `association.
  sim` demo driver (`clojure -M:dev:run`, confirmed by actually running
  it before this file was written -- unlike `cloud-itonami-isic-851`'s
  `schoolops.sim`, this repo's own sim driver uses ids that DO match
  `association.store/demo-data`'s seeded members exactly, and every
  disposition it produces (commit / escalate+approve / HARD hold, and
  the exact `:rule` on each hold) matches `association.governor`'s own
  documented checks precisely, so it was safe to reuse rather than
  author from scratch), trimmed to a representative subset (one clean
  phase-3 auto-commit, the full certification-issuance/disciplinary-
  referral-finalization actuation lifecycle for one member -- both of
  which ALWAYS escalate, never auto, at any phase -- and three distinct
  HARD-hold reasons that never reach a human) and rendered
  deterministically -- no invented numbers, no timestamps in the page
  content, byte-identical across reruns against the same seed (verified
  by diffing two consecutive runs before shipping).

  Usage: `clojure -M:dev:render-html [out-file]`
  (default `docs/samples/operator-console.html`)."
  (:require [clojure.string :as str]
            [association.store :as store]
            [association.operation :as op]
            [langgraph.graph :as g]))

;; ----------------------------- harness (unchanged across every repo
;; in this cluster -- do not rewrite, only copy) -----------------------

(def ^:private operator
  {:actor-id "op-1" :actor-role :association-officer :phase 3})

(defn- exec! [actor tid request]
  (g/run* actor {:request request :context operator} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "op-1"}}
          {:thread-id tid :resume? true}))

(defn run-demo!
  "Runs a fresh seeded store through a scenario mixing every disposition
  this actor can reach, using ONLY real member ids from
  `association.store/demo-data`:

  member-1 (JPN, clean, 40/30 CE hours, no unresolved ethics complaint)
  walks the full clean lifecycle: a `:member/intake` directory-
  normalization patch is a phase-3, no-capital-risk auto-commit
  (governor clean, `:member/intake` is the ONLY op in phase 3's `:auto`
  set); `:jurisdiction/assess` (JPN has a real spec-basis in
  `association.facts`) and `:complaint/screen` (clean) each ALWAYS
  escalate (neither op is ever auto-eligible, at any phase) and are
  approved by a human association officer; `:certification/issue` and
  `:discipline/finalize` -- the two REAL-WORLD actuation events this
  actor performs (a real professional certification issuance / a real
  disciplinary-referral finalization) -- ALSO ALWAYS escalate (the
  governor's own `high-stakes` gate AND the phase table agree,
  independently, that actuation is never auto, at any phase) and are
  each approved, producing one draft certification-issuance record
  (`JPN-CRT-000000`) and one draft disciplinary-referral-finalization
  record (`JPN-DSC-000000`).

  Then three DISTINCT HARD-hold reasons, none of which ever reach a
  human (a human approver cannot override a HARD violation):
    - member-2 (jurisdiction ATL, not in `association.facts/catalog`):
      `:jurisdiction/assess` HARD-holds on `:no-spec-basis` -- the
      advisor may not invent a jurisdiction's professional-body
      governance requirements.
    - member-3 (JPN, 20/30 CE hours): assessed first (clean
      escalate+approve, so evidence is on file and this HARD hold below
      is isolated to the CE-hours check alone), then
      `:certification/issue` HARD-holds on
      `:continuing-education-insufficient` -- the governor
      independently recomputes the member's own completed
      continuing-education hours against the association's own
      recorded minimum, never trusting the advisor's confidence alone.
    - member-4 (`:complaint-unresolved? true` in the seed data):
      `:complaint/screen` HARD-holds on `:complaint-unresolved` -- an
      unresolved ethics/conduct complaint blocks progress,
      un-overridably, even though the screening op itself is the one
      that (re)discovers it.

  Returns the resulting store -- every field `render` below reads is
  real governor/store output, not a hand-typed copy."
  []
  (let [db (store/seed-db)
        actor (op/build db)]

    ;; member-1: clean directory-normalization patch -- phase-3
    ;; auto-commit, no capital risk yet.
    (exec! actor "m1-intake" {:op :member/intake :subject "member-1"
                               :patch {:id "member-1" :member-name "Sakura Tanaka"}})

    ;; member-1: jurisdiction professional-body-governance assessment
    ;; (JPN has a real spec-basis) -- ALWAYS escalates, approved by a
    ;; human.
    (exec! actor "m1-assess" {:op :jurisdiction/assess :subject "member-1"})
    (approve! actor "m1-assess")

    ;; member-1: ethics-complaint screening, clean -- ALWAYS escalates,
    ;; approved by a human.
    (exec! actor "m1-screen" {:op :complaint/screen :subject "member-1"})
    (approve! actor "m1-screen")

    ;; member-1: REAL certification issuance (actuation/issue-
    ;; certification, a real professional-record act) -- ALWAYS
    ;; escalates regardless of phase or confidence, approved by a human
    ;; association officer.
    (exec! actor "m1-certify" {:op :certification/issue :subject "member-1"})
    (approve! actor "m1-certify")

    ;; member-1: REAL disciplinary-referral finalization (actuation/
    ;; finalize-disciplinary-referral) -- ALWAYS escalates, approved by
    ;; a human.
    (exec! actor "m1-discipline" {:op :discipline/finalize :subject "member-1"})
    (approve! actor "m1-discipline")

    ;; member-2 (ATL): no official spec-basis in association.facts ->
    ;; HARD hold on :no-spec-basis, never reaches a human.
    (exec! actor "m2-assess" {:op :jurisdiction/assess :subject "member-2"})

    ;; member-3: assess JPN first (clean escalate+approve) so evidence
    ;; is on file and the CE-hours-insufficient hold below is isolated.
    (exec! actor "m3-assess" {:op :jurisdiction/assess :subject "member-3"})
    (approve! actor "m3-assess")

    ;; member-3: 20/30 completed CE hours -> HARD hold on
    ;; :continuing-education-insufficient, never reaches a human.
    (exec! actor "m3-certify" {:op :certification/issue :subject "member-3"})

    ;; member-4: seeded with an unresolved ethics complaint -> HARD hold
    ;; on :complaint-unresolved, never reaches a human.
    (exec! actor "m4-screen" {:op :complaint/screen :subject "member-4"})

    db))

;; ----------------------------- rendering -----------------------------

(defn- esc [v]
  (-> (str v)
      (str/replace "&" "&amp;")
      (str/replace "<" "&lt;")
      (str/replace ">" "&gt;")))

(defn- last-fact-for [ledger subject-id]
  (last (filter #(= (:subject %) subject-id) ledger)))

(defn- status-cell [ledger subject-id]
  (let [f (last-fact-for ledger subject-id)]
    (cond
      (nil? f) "<span class=\"muted\">no activity</span>"
      (= :committed (:t f)) "<span class=\"ok\">committed</span>"
      (= :approval-granted (:t f)) "<span class=\"ok\">approved &amp; committed</span>"
      (= :governor-hold (:t f))
      (let [rule (-> f :violations first :rule)]
        (str "<span class=\"critical\">HARD hold &middot; " (esc (name (or rule :unknown))) "</span>"))
      (= :approval-requested (:t f)) "<span class=\"warn\">awaiting approval</span>"
      :else "<span class=\"muted\">in progress</span>")))

(defn- member-row [ledger {:keys [id member-name jurisdiction ce-hours-completed ce-hours-required
                                   complaint-unresolved? certified? disciplined?]}]
  (format "        <tr><td>%s</td><td>%s</td><td>%s</td><td>%s / %s</td><td>%s</td><td>%s / %s</td><td>%s</td></tr>"
          (esc id) (esc member-name) (esc jurisdiction)
          (esc ce-hours-completed) (esc ce-hours-required)
          (if complaint-unresolved? "<span class=\"critical\">unresolved</span>" "<span class=\"ok\">clear</span>")
          (if certified? "certified" "not certified") (if disciplined? "disciplined" "not disciplined")
          (status-cell ledger id)))

(defn- ledger-row [{:keys [t op subject disposition basis]}]
  (format "        <tr><td>%s</td><td><code>%s</code></td><td>%s</td><td>%s</td></tr>"
          (esc (name t)) (esc (name (or op :n-a))) (esc subject)
          (esc (or (some->> basis (map #(if (keyword? %) (name %) %)) (str/join ", "))
                    (some-> disposition name) ""))))

(defn- record-row [prefix {:strs [record_id member_id jurisdiction kind immutable]}]
  (format "        <tr><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td></tr>"
          (esc prefix) (esc record_id) (esc member_id) (esc jurisdiction)
          (if immutable "<span class=\"ok\">immutable draft</span>" (esc kind))))

(def ^:private action-gate-rows
  ;; Static description of this actor's own op contract
  ;; (`association.governor`/`association.phase`) -- documentation of
  ;; fixed behavior, not runtime telemetry, so it is legitimately
  ;; hand-described rather than derived from a live run.
  ["        <tr><td><code>:member/intake</code></td><td><span class=\"ok\">phase-3 auto-commit when clean, no capital risk yet -- the ONLY auto-eligible op in this domain</span></td></tr>"
   "        <tr><td><code>:jurisdiction/assess</code></td><td><span class=\"warn\">ALWAYS human approval &middot; spec-basis independently checked against <code>association.facts</code>, never fabricated</span></td></tr>"
   "        <tr><td><code>:complaint/screen</code></td><td><span class=\"warn\">ALWAYS human approval when clean &middot; an unresolved ethics complaint is a HARD, un-overridable hold instead</span></td></tr>"
   "        <tr><td><code>:certification/issue</code></td><td><span class=\"warn\">ALWAYS human approval &middot; real professional-record act (actuation/issue-certification) &middot; CE-hours sufficiency + double-certification guard independently enforced, never auto at any phase</span></td></tr>"
   "        <tr><td><code>:discipline/finalize</code></td><td><span class=\"warn\">ALWAYS human approval &middot; real professional-record act (actuation/finalize-disciplinary-referral) &middot; evidence-completeness + double-referral guard enforced, never auto at any phase</span></td></tr>"])

(defn render
  "Renders the full operator-console.html document from a store `db`
  that has already run `run-demo!` (or any other real scenario)."
  [db]
  (let [ledger (vec (store/ledger db))
        members (store/all-members db)
        member-rows (str/join "\n" (map (partial member-row ledger) members))
        ledger-rows (str/join "\n" (map ledger-row ledger))
        certification-rows (str/join "\n" (map (partial record-row "certification") (store/certification-history db)))
        discipline-rows (str/join "\n" (map (partial record-row "discipline") (store/discipline-history db)))]
    (str
     "<html><head><meta charset=\"utf-8\"><title>cloud-itonami-isic-9412 &middot; activities of professional membership organizations</title><style>\n"
     "table { width: 100%; border-collapse: collapse; font-size: 14px; }\n"
     ".ok { color: #137a3f; }\n"
     "body { font-family: system-ui,-apple-system,sans-serif; margin: 0; color: #1a1a1a; background: #fafafa; }\n"
     "header.bar { display: flex; align-items: center; gap: 12px; padding: 12px 20px; background: #fff; border-bottom: 1px solid #e5e5e5; }\n"
     "th, td { text-align: left; padding: 8px 10px; border-bottom: 1px solid #f0f0f0; }\n"
     "h2 { margin-top: 0; font-size: 15px; }\n"
     ".warn { color: #b25c00; background: #fff8e1; padding: 2px 6px; border-radius: 4px; }\n"
     "main { max-width: 980px; margin: 24px auto; padding: 0 20px; }\n"
     "header.bar h1 { font-size: 18px; margin: 0; font-weight: 600; }\n"
     ".muted { color: #888; font-size: 13px; }\n"
     ".critical { color: #fff; background: #b3261e; padding: 2px 6px; border-radius: 4px; font-weight: 600; }\n"
     ".card { background: #fff; border: 1px solid #e5e5e5; border-radius: 8px; padding: 16px; margin-bottom: 16px; }\n"
     ".err { color: #b3261e; background: #fbe9e7; padding: 2px 6px; border-radius: 4px; }\n"
     "th { font-weight: 600; color: #555; font-size: 12px; text-transform: uppercase; letter-spacing: 0.04em; }\n"
     "header.bar .badge { margin-left: auto; font-size: 12px; color: #666; }\n"
     "code { font-size: 12px; background: #f4f4f4; padding: 1px 4px; border-radius: 3px; }\n"
     "</style></head><body>\n"
     "<header class=\"bar\">\n"
     "  <h1>Activities of professional membership organizations (ISIC 9412) — Operator Console</h1>\n"
     "  <span class=\"badge\">read-only sample · governor-gated · certification issuance/disciplinary referral always human-approved</span>\n"
     "</header>\n"
     "<main>\n"
     "  <section class=\"card\">\n"
     "    <h2>Members</h2>\n"
     "    <p class=\"muted\">Demo snapshot — build-time-generated from <code>association.store</code> via <code>association.render-html</code> (<code>clojure -M:dev:render-html</code>), regenerated nightly.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Member</th><th>Name</th><th>Jurisdiction</th><th>CE hours (completed/required)</th><th>Ethics complaint</th><th>Certification / Discipline</th><th>Last op status</th></tr></thead>\n"
     "      <tbody>\n"
     member-rows "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"
     "  <section class=\"card\">\n"
     "    <h2>Draft certification-issuance / disciplinary-referral-finalization records</h2>\n"
     "    <p class=\"muted\">Unsigned drafts only — the association's own act of signing is outside this actor's authority (see README <code>Actuation</code>).</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Kind</th><th>Record id</th><th>Member</th><th>Jurisdiction</th><th>Status</th></tr></thead>\n"
     "      <tbody>\n"
     certification-rows (when (seq certification-rows) "\n")
     discipline-rows "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"
     "  <section class=\"card\">\n"
     "    <h2>Action gate (Association Governance Governor)</h2>\n"
     "    <p class=\"muted\">HARD holds cannot be overridden by a human approver. Jurisdiction spec-basis, continuing-education-hours sufficiency, evidence completeness and unresolved ethics complaints are independently recomputed, never trusted from the advisor's proposal; a real certification issuance or disciplinary-referral finalization is always a human association officer's call, at every rollout phase.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Op</th><th>Gate</th></tr></thead>\n"
     "      <tbody>\n"
     (str/join "\n" action-gate-rows) "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"
     "  <section class=\"card\">\n"
     "    <h2>Audit ledger (this run)</h2>\n"
     "    <p class=\"muted\">Append-only decision-fact log — every proposal, hold and commit this scenario produced.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Fact</th><th>Op</th><th>Subject</th><th>Basis</th></tr></thead>\n"
     "      <tbody>\n"
     ledger-rows "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"
     "</main>\n"
     "</body></html>\n")))

(defn -main [& args]
  (let [out (or (first args) "docs/samples/operator-console.html")
        db (run-demo!)
        html (render db)]
    (spit out html)
    (println "wrote" out "(" (count (store/ledger db)) "ledger facts,"
             (count (store/certification-history db)) "certification drafts,"
             (count (store/discipline-history db)) "discipline drafts )")))
