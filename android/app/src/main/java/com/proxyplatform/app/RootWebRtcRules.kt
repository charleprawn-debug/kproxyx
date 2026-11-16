package com.proxyplatform.app

/** Pure, testable shell generation. Never interpolate proxy credentials or shell input. */
internal object RootWebRtcRules {
    const val CHAIN = "KPX_WEBRTC_BLOCK"

    fun apply(appUid: Int): String {
        require(appUid >= 10000) { "Expected an Android application UID" }
        return """
            set -e
            for BIN in iptables ip6tables; do
              command -v "${'$'}BIN" >/dev/null 2>&1 || exit 41
              ${'$'}BIN -w 5 -S OUTPUT >/dev/null
              # Never flush a live chain: an existing session must be restored first.
              ${'$'}BIN -w 5 -N $CHAIN
              ${'$'}BIN -w 5 -A $CHAIN -o lo -j RETURN
              ${'$'}BIN -w 5 -A $CHAIN -p tcp -m owner --uid-owner $appUid -j RETURN
              # Android's system resolver only, not arbitrary apps on port 53.
              for DNS_UID in 0 1000 1051; do
                ${'$'}BIN -w 5 -A $CHAIN -p udp --dport 53 -m owner --uid-owner "${'$'}DNS_UID" -j RETURN
                ${'$'}BIN -w 5 -A $CHAIN -p tcp --dport 53 -m owner --uid-owner "${'$'}DNS_UID" -j RETURN
              done
              if [ "${'$'}BIN" = iptables ]; then
                ${'$'}BIN -w 5 -A $CHAIN -p udp --sport 68 --dport 67 -m owner --uid-owner 0 -j RETURN
                ${'$'}BIN -w 5 -A $CHAIN -p udp --sport 68 --dport 67 -m owner --uid-owner 1000 -j RETURN
              else
                ${'$'}BIN -w 5 -A $CHAIN -p udp --sport 546 --dport 547 -m owner --uid-owner 0 -j RETURN
                ${'$'}BIN -w 5 -A $CHAIN -p udp --sport 546 --dport 547 -m owner --uid-owner 1000 -j RETURN
              fi
              ${'$'}BIN -w 5 -A $CHAIN -p udp -j REJECT
              ${'$'}BIN -w 5 -A $CHAIN -p tcp -j REJECT
            done
            # Build both families completely before attaching either one.
            for BIN in iptables ip6tables; do
              ${'$'}BIN -w 5 -I OUTPUT 1 -j $CHAIN
              ${'$'}BIN -w 5 -C OUTPUT -j $CHAIN
            done
        """.trimIndent()
    }

    /**
     * Only remove our chain/jumps. Missing chain is success; missing tools,
     * denied access, lock failure and failed deletions must remain recoverable.
     */
    val restore = """
        FAILED=0
        for BIN in iptables ip6tables; do
          if ! command -v "${'$'}BIN" >/dev/null 2>&1; then FAILED=1; continue; fi
          if ! ${'$'}BIN -w 5 -S OUTPUT >/dev/null; then FAILED=1; continue; fi
          while ${'$'}BIN -w 5 -C OUTPUT -j $CHAIN 2>/dev/null; do
            ${'$'}BIN -w 5 -D OUTPUT -j $CHAIN || { FAILED=1; break; }
          done
          if ${'$'}BIN -w 5 -S $CHAIN >/dev/null 2>&1; then
            ${'$'}BIN -w 5 -F $CHAIN || FAILED=1
            ${'$'}BIN -w 5 -X $CHAIN || FAILED=1
          fi
          # Verify against the complete ruleset, not -C alone (which may fail).
          RULES=${'$'}(${'$'}BIN -w 5 -S) || { FAILED=1; continue; }
          case "${'$'}RULES" in *$CHAIN*) FAILED=1 ;; esac
        done
        [ "${'$'}FAILED" -eq 0 ] || { echo 'تعذرت إزالة قواعد الحماية بالكامل؛ أعد المحاولة بصلاحية الروت.'; exit 43; }
    """.trimIndent()
}
