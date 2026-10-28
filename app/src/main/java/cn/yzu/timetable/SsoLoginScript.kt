package cn.yzu.timetable

import java.nio.charset.StandardCharsets
import java.util.Base64

/** Builds the SSO submit script without embedding raw credentials in JavaScript source. */
object SsoLoginScript {
    fun build(account: String, password: String): String {
        val encodedAccount = Base64.getEncoder().encodeToString(account.toByteArray(StandardCharsets.UTF_8))
        val encodedPassword = Base64.getEncoder().encodeToString(password.toByteArray(StandardCharsets.UTF_8))
        return """
            (function() {
              var account = decodeURIComponent(escape(atob('$encodedAccount')));
              var password = decodeURIComponent(escape(atob('$encodedPassword')));
              var user = document.querySelector('input[name="username"]');
              var pass = document.querySelector('input[type="password"]');
              var form = pass && pass.form;
              if (!user || !pass || !form) return 'WAIT';
              var setValue = function(element, value) {
                var setter = Object.getOwnPropertyDescriptor(element.__proto__, 'value');
                if (setter && setter.set) setter.set.call(element, value); else element.value = value;
                element.dispatchEvent(new Event('input', {bubbles:true}));
                element.dispatchEvent(new Event('change', {bubbles:true}));
              };
              setValue(user, account);
              setValue(pass, password);
              var button = form.querySelector('button[type="submit"]') || form.querySelector('button');
              if (button) { button.click(); return 'SUBMITTED'; }
              form.submit(); return 'SUBMITTED';
            })();
        """.trimIndent()
    }
}
