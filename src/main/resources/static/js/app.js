(() => {
    const themeKey = 'releaseflow:theme';

    const currentTheme = () => document.documentElement.dataset.theme === 'dark' ? 'dark' : 'light';

    const toggleTheme = () => {
        const next = currentTheme() === 'dark' ? 'light' : 'dark';
        document.documentElement.dataset.theme = next;
        try {
            localStorage.setItem(themeKey, next);
        } catch (ignored) {
            // Storage can be unavailable in private windows; the theme still applies to this page.
        }
        window.dispatchEvent(new CustomEvent('theme:changed', { detail: { theme: next } }));
        return next;
    };

    // Invitation links carry the token in the URL fragment, which browsers never send to
    // the server. Move it into the server-rendered form, drop it from the address bar and
    // history, then submit. Without JavaScript the invitee pastes the code instead.
    document.addEventListener('DOMContentLoaded', () => {
        const form = document.querySelector('form[data-invitation-open]');
        const match = /^#token=([A-Za-z0-9_-]{1,128})$/.exec(window.location.hash);
        if (!form || !match) return;
        form.elements.token.value = match[1];
        history.replaceState(null, '', window.location.pathname);
        form.requestSubmit();
    });

    document.addEventListener('alpine:init', () => {
        // Every label is rendered on the server and read from the element's data
        // attributes, so no English wording is kept in this file.
        Alpine.data('themeToggle', () => ({
            theme: currentTheme(),
            labels: { light: '', dark: '' },
            init() {
                this.labels = {
                    light: this.$el.dataset.labelLight ?? '',
                    dark: this.$el.dataset.labelDark ?? '',
                };
                window.addEventListener('theme:changed', (event) => { this.theme = event.detail.theme; });
                window.addEventListener('storage', (event) => {
                    if (event.key === themeKey && (event.newValue === 'dark' || event.newValue === 'light')) {
                        document.documentElement.dataset.theme = event.newValue;
                        this.theme = event.newValue;
                    }
                });
            },
            get label() { return this.theme === 'dark' ? this.labels.light : this.labels.dark; },
            toggle() { this.theme = toggleTheme(); },
        }));

        // Kept under explicit state rather than DaisyUI's focus-driven dropdown
        // so the sign-out form inside the panel stays reachable by keyboard.
        Alpine.data('userMenu', () => ({
            open: false,
            toggle() { this.open ? this.close(false) : this.show(); },
            show() { this.open = true; },
            close(focusTrigger) {
                if (!this.open) return;
                this.open = false;
                if (focusTrigger) this.$refs.trigger.focus();
            },
            closeOnFocusOut(event) {
                if (!this.$el.contains(event.relatedTarget)) this.close(false);
            },
        }));

        Alpine.data('passwordField', () => ({
            visible: false,
            labels: { show: '', hide: '' },
            init() {
                this.labels = {
                    show: this.$el.dataset.labelShow ?? '',
                    hide: this.$el.dataset.labelHide ?? '',
                };
            },
            get label() { return this.visible ? this.labels.hide : this.labels.show; },
            toggle() { this.visible = !this.visible; },
        }));

        // Inserts a template variable at the caret and warns before leaving with unsaved
        // edits. The variable name comes from the button's data attribute, never from
        // an Alpine expression.
        Alpine.data('audienceEditor', () => ({
            dirty: false,
            init() {
                window.addEventListener('beforeunload', (event) => {
                    if (this.dirty) event.preventDefault();
                });
            },
            insert(variable) {
                const template = this.$refs.template;
                const token = `{{${variable}}}`;
                const start = template.selectionStart ?? template.value.length;
                const end = template.selectionEnd ?? start;
                template.setRangeText(token, start, end, 'end');
                template.focus();
                this.dirty = true;
            },
        }));

        // Checks the release's notes every three seconds while some are still being
        // translated, and reloads the page once none is. The URL comes from the element's
        // data attribute, never from an Alpine expression.
        Alpine.data('translationPoll', () => ({
            timer: null,
            init() {
                const url = this.$el.dataset.pollUrl;
                const stopAt = Date.now() + 10 * 60 * 1000;
                this.timer = setInterval(async () => {
                    if (Date.now() > stopAt) {
                        clearInterval(this.timer);
                        return;
                    }
                    try {
                        const response = await fetch(url, { headers: { Accept: 'application/json' } });
                        if (!response.ok) return;
                        const notes = await response.json();
                        if (!notes.some((note) => note.translationStatus === 'PENDING')) {
                            clearInterval(this.timer);
                            window.location.reload();
                        }
                    } catch (ignored) {
                        // A missed check is retried on the next tick.
                    }
                }, 3000);
            },
            destroy() { clearInterval(this.timer); },
        }));

        // Reads the value from the element marked x-ref="source" so server data
        // never has to be interpolated into an Alpine expression.
        Alpine.data('copyText', () => ({
            copied: false,
            labels: { copy: '', copied: '' },
            init() {
                this.labels = {
                    copy: this.$el.dataset.labelCopy ?? '',
                    copied: this.$el.dataset.labelCopied ?? '',
                };
            },
            async copy() {
                const source = this.$refs.source;
                try {
                    await navigator.clipboard.writeText(source.value ?? source.textContent.trim());
                } catch (ignored) {
                    source.select?.();
                    return;
                }
                this.copied = true;
                setTimeout(() => { this.copied = false; }, 2000);
            },
            get label() { return this.copied ? this.labels.copied : this.labels.copy; },
        }));
    });
})();
