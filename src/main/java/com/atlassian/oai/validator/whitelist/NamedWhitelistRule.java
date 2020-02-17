package com.atlassian.oai.validator.whitelist;

import com.atlassian.oai.validator.whitelist.rule.WhitelistRule;

import java.util.Objects;

public class NamedWhitelistRule {
    private final String name;
    private final WhitelistRule rule;

    @Override
    public String toString() {
        return name + " (" + rule + ")";
    }

    NamedWhitelistRule(final String name, final WhitelistRule rule) {
        this.name = Objects.requireNonNull(name);
        this.rule = Objects.requireNonNull(rule);
    }

    public String getName() {
        return name;
    }

    public WhitelistRule getRule() {
        return rule;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        final NamedWhitelistRule that = (NamedWhitelistRule) o;

        return Objects.equals(this.getName(), that.getName()) &&
                Objects.equals(this.getRule(), that.getRule());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getName(), getRule());
    }
}
