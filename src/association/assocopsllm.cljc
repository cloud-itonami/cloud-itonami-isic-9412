(ns association.assocopsllm
  "AssocOps-LLM client -- the *contained intelligence node* for the
  association actor.

  It normalizes member-intake, drafts a per-jurisdiction professional-
  body-governance evidence checklist, screens members for an
  unresolved ethics complaint, drafts the certification-issuance
  action, and drafts the disciplinary-referral-finalization action.
  CRITICAL: it is a smart-but-untrusted advisor. It returns a
  *proposal* (with a rationale + the fields it cited), never a
  committed record or a real certification/disciplinary-referral.
  Every output is censored downstream by `association.governor` before
  anything touches the SSoT, and `:certification/issue`/`:discipline/
  finalize` proposals NEVER auto-commit at any phase -- see README
  `Actuation`.

  Like every sibling actor's advisor, this is a deterministic mock so
  the actor graph runs offline and the governor contract is exercised
  end-to-end. In production this calls a real LLM (kotoba-llm or
  equivalent) with the same proposal shape.

  Proposal shape (all kinds):
    {:summary    str            ; human-facing draft / finding
     :rationale  str            ; why -- SCANNED by the spec-basis gate
     :cites      [kw|str ..]    ; facts/sources the LLM used -- SCANNED too
     :effect     kw             ; how a commit would mutate the SSoT
     :stake      kw|nil         ; :actuation/issue-certification | :actuation/finalize-disciplinary-referral | nil
     :confidence 0..1}"
  (:require #?(:clj  [clojure.edn :as edn]
               :cljs [cljs.reader :as edn])
            [clojure.string :as str]
            [association.facts :as facts]
            [association.registry :as registry]
            [association.store :as store]
            [langchain.model :as model]))

(defn- normalize-intake
  "Directory upsert -- the LLM only normalizes/validates the patch; it
  does not invent the member, CE-hours figures or jurisdiction. High
  confidence, low stakes."
  [_db {:keys [patch]}]
  {:summary    (str "会員記録更新: " (pr-str (keys patch)))
   :rationale  "入力 patch の正規化のみ。新規事実の生成なし。"
   :cites      (vec (keys patch))
   :effect     :member/upsert
   :value      patch
   :stake      nil
   :confidence 0.97})

(defn- assess-jurisdiction
  "Per-jurisdiction professional-body-governance evidence checklist
  draft. `:no-spec?` injects the failure mode we must defend against:
  proposing a checklist for a jurisdiction with NO official spec-basis
  in `association.facts` -- the Association Governance Governor must
  reject this (never invent a jurisdiction's requirements)."
  [db {:keys [subject no-spec?]}]
  (let [m (store/member db subject)
        iso3 (if no-spec? "ATL" (:jurisdiction m))
        sb (facts/spec-basis iso3)]
    (if (nil? sb)
      {:summary    (str iso3 " の公式spec-basisが見つかりません")
       :rationale  "association.facts に未登録の法域。要件を推測で作らない。"
       :cites      []
       :effect     :assessment/set
       :value      {:jurisdiction iso3 :checklist [] :spec-basis nil}
       :stake      nil
       :confidence 0.9}
      {:summary    (str iso3 " (" (:owner-authority sb) ") 向け必要書類 "
                        (count (:required-evidence sb)) " 件を提案")
       :rationale  (str "公式ソース: " (:provenance sb) " / 法的根拠: " (:legal-basis sb))
       :cites      [(:legal-basis sb) (:provenance sb)]
       :effect     :assessment/set
       :value      {:jurisdiction iso3
                    :checklist (:required-evidence sb)
                    :spec-basis (:provenance sb)
                    :legal-basis (:legal-basis sb)}
       :stake      nil
       :confidence 0.9})))

(defn- screen-complaint
  "Ethics-complaint screening draft. `:complaint-unresolved?` on the
  member record injects the failure mode: the Association Governance
  Governor must HOLD, un-overridably, on any unresolved complaint."
  [db {:keys [subject]}]
  (let [m (store/member db subject)]
    (cond
      (nil? m)
      {:summary "対象会員記録が見つかりません" :rationale "no member record"
       :cites [] :effect :complaint/set :value {:member-id subject :verdict :unknown}
       :stake nil :confidence 0.0}

      (true? (:complaint-unresolved? m))
      {:summary    (str (:member-name m) ": 未解決の倫理苦情を検出")
       :rationale  "スクリーニングが未解決の倫理・行動規範苦情を検出。人手確認とホールドが必須。"
       :cites      [:complaint-check]
       :effect     :complaint/set
       :value      {:member-id subject :verdict :unresolved}
       :stake      nil
       :confidence 0.95}

      :else
      {:summary    (str (:member-name m) ": 未解決の倫理苦情なし")
       :rationale  "倫理苦情スクリーニング完了。"
       :cites      [:complaint-check]
       :effect     :complaint/set
       :value      {:member-id subject :verdict :resolved}
       :stake      nil
       :confidence 0.9})))

(defn- propose-certification-issuance
  "Draft the actual CERTIFICATION-ISSUANCE action -- issuing a real
  professional certification to a member. ALWAYS `:stake :actuation/
  issue-certification` -- this is a REAL-WORLD member-record act,
  never a draft the actor may auto-run. See README `Actuation`: no
  phase ever adds this op to a phase's `:auto` set (`association.
  phase`); the governor also always escalates on `:actuation/issue-
  certification`. Two independent layers agree, deliberately."
  [db {:keys [subject]}]
  (let [m (store/member db subject)]
    {:summary    (str subject " 向け資格認定発行提案"
                      (when m (str " (member=" (:member-name m) ")")))
     :rationale  (if m
                   (str "ce-hours-completed=" (:ce-hours-completed m)
                        " ce-hours-required=" (:ce-hours-required m))
                   "会員記録が見つかりません")
     :cites      (if m [subject] [])
     :effect     :member/mark-certified
     :value      {:member-id subject}
     :stake      :actuation/issue-certification
     :confidence (if (and m (not (registry/continuing-education-hours-insufficient? m))) 0.9 0.3)}))

(defn- propose-disciplinary-referral-finalization
  "Draft the actual DISCIPLINARY-REFERRAL-FINALIZATION action --
  finalizing a real disciplinary referral against a member. ALWAYS
  `:stake :actuation/finalize-disciplinary-referral` -- this is a
  REAL-WORLD member-record act, never a draft the actor may auto-run.
  See README `Actuation`: no phase ever adds this op to a phase's
  `:auto` set (`association.phase`); the governor also always
  escalates on `:actuation/finalize-disciplinary-referral`. Two
  independent layers agree, deliberately."
  [db {:keys [subject]}]
  (let [m (store/member db subject)]
    {:summary    (str subject " 向け懲戒付託確定提案"
                      (when m (str " (member=" (:member-name m) ")")))
     :rationale  (if m
                   "jurisdiction-evidence-checklist referenced"
                   "会員記録が見つかりません")
     :cites      (if m [subject] [])
     :effect     :member/mark-disciplined
     :value      {:member-id subject}
     :stake      :actuation/finalize-disciplinary-referral
     :confidence (if m 0.9 0.3)}))

(defn infer
  "Route a request to the right proposal generator.
  request: {:op kw :subject id ...op-specific...}"
  [db {:keys [op] :as request}]
  (case op
    :member/intake         (normalize-intake db request)
    :jurisdiction/assess    (assess-jurisdiction db request)
    :complaint/screen       (screen-complaint db request)
    :certification/issue   (propose-certification-issuance db request)
    :discipline/finalize    (propose-disciplinary-referral-finalization db request)
    {:summary "未対応の操作" :rationale (str op) :cites []
     :effect :noop :stake nil :confidence 0.0}))

;; ----------------------------- Advisor protocol -----------------------------

(defprotocol Advisor
  (-advise [advisor store request] "store + request -> proposal map"))

(defn mock-advisor
  "The deterministic advisor (the `infer` logic above). Default everywhere."
  [] (reify Advisor (-advise [_ st req] (infer st req))))

(def ^:private system-prompt
  (str "あなたは職能団体の資格認定発行・懲戒付託確定エージェントの助言者です。"
       "与えられた事実のみに基づき、提案を1つだけEDNマップで返します。説明や前置きは"
       "一切書かず、EDNだけを出力します。\n"
       "キー: :summary(人向けドラフト) :rationale(根拠/必ず事実から) "
       ":cites(使った事実キーのベクタ) "
       ":effect(:member/upsert|:assessment/set|:complaint/set|"
       ":member/mark-certified|:member/mark-disciplined) "
       ":stake(:actuation/issue-certification か :actuation/finalize-disciplinary-referral か nil) :confidence(0..1)。\n"
       "重要: 登録されていない法域の要件を絶対に創作してはいけません。"
       "spec-basisが無い場合は :cites を空にし confidence を上げないこと。"))

(defn- facts-for [st {:keys [op subject]}]
  (case op
    :jurisdiction/assess   {:member (store/member st subject)}
    :complaint/screen      {:member (store/member st subject)}
    :certification/issue   {:member (store/member st subject)}
    :discipline/finalize   {:member (store/member st subject)}
    {:member (store/member st subject)}))

(defn- parse-proposal
  "Parse the model's EDN proposal defensively. Any parse/shape failure
  yields a safe low-confidence noop so the Association Governance
  Governor escalates/holds -- an LLM hiccup can never auto-issue a
  certification or auto-finalize a disciplinary referral."
  [content]
  (let [p (try (edn/read-string (str/trim (str content)))
               (catch #?(:clj Exception :cljs :default) _ nil))]
    (if (map? p)
      (-> p
          (update :cites #(vec (or % [])))
          (update :confidence #(if (number? %) (double %) 0.0))
          (update :effect #(or % :noop)))
      {:summary "LLM応答を解釈できませんでした" :rationale (str content)
       :cites [] :effect :noop :stake nil :confidence 0.0})))

(defn llm-advisor
  "An advisor backed by a `langchain.model/ChatModel` (real inference)."
  ([chat-model] (llm-advisor chat-model {}))
  ([chat-model gen-opts]
   (reify Advisor
     (-advise [_ st req]
       (let [msgs [{:role :system :content system-prompt}
                   {:role :user :content (str "操作: " (:op req)
                                              "\n対象: " (:subject req)
                                              "\n事実: " (pr-str (facts-for st req)))}]
             resp (model/-generate chat-model msgs gen-opts)]
         (parse-proposal (:content resp)))))))

(defn trace
  "Decision-grounded audit record -- persisted to the :audit channel."
  [request proposal]
  {:t          :assocopsllm-proposal
   :op         (:op request)
   :subject    (:subject request)
   :summary    (:summary proposal)
   :rationale  (:rationale proposal)
   :cites      (:cites proposal)
   :confidence (:confidence proposal)})
