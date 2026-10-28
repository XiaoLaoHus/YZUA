package cn.yzu.timetable

import java.nio.charset.StandardCharsets
import java.util.Base64

/** Fill, then click in a later task so Angular validation and official encryption can run. */
object SsoLoginScript {
    fun build(account: String, password: String): String {
        val encodedAccount = Base64.getEncoder().encodeToString(account.toByteArray(StandardCharsets.UTF_8))
        val encodedPassword = Base64.getEncoder().encodeToString(password.toByteArray(StandardCharsets.UTF_8))
        return """
            (function() {
              if (location.protocol !== 'https:' || location.hostname !== 'sso.yzu.edu.cn' ||
                  (location.port && location.port !== '443') || location.pathname !== '/login' ||
                  location.username || location.password) return 'UNTRUSTED';
              var pass = Array.prototype.find.call(document.querySelectorAll('input[type="password"]'),
                function(element) { return element.form && element.getClientRects().length > 0; });
              var form = pass && pass.form;
              var user = form && form.querySelector('input[name="username"]');
              if (!user || !pass || !form) return 'WAIT';
              if (form.__yzuaClicked) return 'CLICKED';
              if (form.__yzuaFilled) {
                var button = form.querySelector('button[type="submit"]') ||
                  form.querySelector('#submitBtn') || form.querySelector('button.login-button');
                if (!button || button.disabled || button.getAttribute('aria-disabled') === 'true' ||
                    (button.getAttribute('title') || '').trim() ||
                    (form.checkValidity && !form.checkValidity())) return 'WAIT';
                return 'READY';
              }
              var account = decodeURIComponent(escape(atob('$encodedAccount')));
              var password = decodeURIComponent(escape(atob('$encodedPassword')));
              var setValue = function(element, value) {
                var setter = Object.getOwnPropertyDescriptor(element.__proto__, 'value');
                if (setter && setter.set) setter.set.call(element, value); else element.value = value;
                element.dispatchEvent(new Event('input', {bubbles:true}));
                element.dispatchEvent(new Event('change', {bubbles:true}));
              };
              setValue(user, account);
              setValue(pass, password);
              pass.dispatchEvent(new Event('blur', {bubbles:true}));
              form.__yzuaFilled = true;
              return 'FILLED';
            })();
        """.trimIndent()
    }

    // The Activity marks its one-shot submission BEFORE this script can trigger navigation.
    val click: String = """
        (function() {
          if (location.protocol !== 'https:' || location.hostname !== 'sso.yzu.edu.cn' ||
              (location.port && location.port !== '443') || location.pathname !== '/login' ||
              location.username || location.password) return 'UNTRUSTED';
          var pass = Array.prototype.find.call(document.querySelectorAll('input[type="password"]'),
            function(element) { return element.form && element.getClientRects().length > 0; });
          var form = pass && pass.form;
          if (!form || !form.__yzuaFilled) return 'WAIT';
          if (form.__yzuaClicked) return 'CLICKED';
          var button = form.querySelector('button[type="submit"]') ||
            form.querySelector('#submitBtn') || form.querySelector('button.login-button');
          if (!button || button.disabled || button.getAttribute('aria-disabled') === 'true' ||
              (button.getAttribute('title') || '').trim()) return 'WAIT';
          form.__yzuaClicked = true;
          button.click();
          return 'CLICKED';
        })();
    """.trimIndent()

    // Return fixed status codes only, never page text, usernames, tickets or cookies.
    val inspect: String = """
        (function() {
          var visible = function(element) { return element.getClientRects().length > 0; };
          var captcha = Array.prototype.find.call(document.querySelectorAll(
            'app-verification, input[name="captcha"], input[name="captcha_code"]:not([type="hidden"]), .geetest_panel'), visible);
          if (captcha) return 'CAPTCHA';
          var errors = document.querySelectorAll('.error-msg, .error-toast, .phone-toast-class, .ant-message-error, .ant-notification-notice-description, [role="alert"]');
          for (var i = 0; i < errors.length; i++) {
            if (!visible(errors[i])) continue;
            var message = (errors[i].textContent || '').trim();
            if (!message) continue;
            if (/验证码|验证后|二次认证|滑块/.test(message)) return 'CAPTCHA';
            return 'ERROR';
          }
          return 'WAIT';
        })();
    """.trimIndent()
}
