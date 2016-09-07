package io.token;

import io.token.proto.common.token.TokenProtos.Token;
import org.junit.Rule;
import org.junit.Test;

import static io.token.asserts.TokenAssertion.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

public class PaymentTokenTest {
    @Rule public TokenRule rule = new TokenRule();
    private final Account payer = rule.account();
    private final Account payee = rule.account();

    @Test
    public void createToken() {
        Token token = payer.createToken(
                100.0,
                "USD",
                payee.getMember().getFirstAlias(),
                "book purchase");

        assertThat(token)
                .hasPayer(payer.getMember())
                .hasRedeemerAlias(payee.getMember().getFirstAlias())
                .hasAmount(100.0)
                .hasCurrency("USD")
                .hasNoSignatures();
    }

    @Test
    public void lookupToken() {
        Token token = payer.createToken(100.0, "USD");
        assertThat(payer.lookupToken(token.getId()))
                .hasPayer(payer.getMember())
                .hasAmount(100.0)
                .hasCurrency("USD")
                .hasNoSignatures();
    }

    @Test
    public void lookupTokens() {
        Token token1 = payer.createToken(123.45, "EUR");
        Token token2 = payer.createToken(678.90, "USD");
        Token token3 = payer.createToken(100.99, "USD");

        assertThat(payer.lookupTokens(0, 100))
                .hasSize(3)
                .containsOnly(token1, token2, token3);
    }

    @Test
    public void endorseToken() {
        Token token = payer.createToken(100.0, "USD");
        token = payer.endorseToken(token);

        assertThat(token)
                .hasNSignatures(2)
                .isEndorsedBy(payer.getMember())
                .hasPayer(payer.getMember())
                .hasAmount(100.0)
                .hasCurrency("USD");
    }

    @Test
    public void declineToken() {
        Token token = payer.createToken(100.0, "USD");
        token = payer.declineToken(token);

        assertThat(token)
                .hasNSignatures(2)
                .isDeclinedBy(payer.getMember())
                .hasPayer(payer.getMember())
                .hasAmount(100.0)
                .hasCurrency("USD");
    }

    @Test
    public void revokeToken() {
        Token token = payer.createToken(100.0, "USD");
        token = payer.endorseToken(token);
        token = payer.revokeToken(token);

        assertThat(token)
                .hasNSignatures(4)
                .isEndorsedBy(payer.getMember())
                .isRevokedBy(payer.getMember())
                .hasPayer(payer.getMember())
                .hasAmount(100.0)
                .hasCurrency("USD");
    }
}
