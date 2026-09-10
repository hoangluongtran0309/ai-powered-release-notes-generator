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

    document.addEventListener('alpine:init', () => {
        Alpine.data('themeToggle', () => ({
            theme: currentTheme(),
            init() {
                window.addEventListener('theme:changed', (event) => { this.theme = event.detail.theme; });
                window.addEventListener('storage', (event) => {
                    if (event.key === themeKey && (event.newValue === 'dark' || event.newValue === 'light')) {
                        document.documentElement.dataset.theme = event.newValue;
                        this.theme = event.newValue;
                    }
                });
            },
            get label() { return this.theme === 'dark' ? 'Switch to light theme' : 'Switch to dark theme'; },
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
            get label() { return this.visible ? 'Hide' : 'Show'; },
            toggle() { this.visible = !this.visible; },
        }));

        // Reads the value from the element marked x-ref="source" so server data
        // never has to be interpolated into an Alpine expression.
        Alpine.data('copyText', () => ({
            copied: false,
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
            get label() { return this.copied ? 'Copied' : 'Copy'; },
        }));
    });
})();
