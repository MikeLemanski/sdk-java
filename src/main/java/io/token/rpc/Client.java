package io.token.rpc;

import io.token.proto.common.account.AccountProtos.Account;
import io.token.proto.common.device.DeviceProtos;
import io.token.proto.common.member.MemberProtos;
import io.token.proto.common.member.MemberProtos.*;
import io.token.proto.common.money.MoneyProtos.Money;
import io.token.proto.common.paging.PagingProtos.Page;
import io.token.proto.common.payment.PaymentProtos.Payment;
import io.token.proto.common.payment.PaymentProtos.PaymentPayload;
import io.token.proto.common.security.SecurityProtos.Key.Level;
import io.token.proto.common.security.SecurityProtos.Signature;
import io.token.proto.common.token.TokenProtos.Token;
import io.token.proto.common.token.TokenProtos.TokenPayload;
import io.token.proto.common.transaction.TransactionProtos.Transaction;
import io.token.proto.gateway.Gateway.*;
import io.token.proto.gateway.GatewayServiceGrpc.GatewayServiceFutureStub;
import io.token.security.SecretKey;
import io.token.util.codec.ByteEncoding;
import rx.Observable;

import javax.annotation.Nullable;
import java.util.List;

import static io.token.proto.common.token.TokenProtos.TokenSignature.Action.CANCELLED;
import static io.token.proto.common.token.TokenProtos.TokenSignature.Action.ENDORSED;
import static io.token.rpc.util.Converters.toObservable;
import static io.token.security.Crypto.sign;

/**
 * An authenticated RPC client that is used to talk to Token gateway. The
 * class is a thin wrapper on top of gRPC generated client. Makes the API
 * easier to use.
 */
public final class Client {
    private final SecretKey key;
    private final GatewayServiceFutureStub gateway;
    private String onBehalfOf;

    /**
     * @param key secret key that is used to sign payload for certain requests.
     *            This is generally the same key that is used for
     *            authentication.
     * @param gateway gateway gRPC stub
     */
    public Client(SecretKey key, GatewayServiceFutureStub gateway) {
        this.key = key;
        this.gateway = gateway;
    }

    /**
     * Sets the On-Behalf-Of authentication value to be used
     * with this client.  The value must correspond to an existing
     * Access Token ID issued for the client member.
     *
     * @param accessTokenId the access token id to be used
     */
    public void useAccessToken(String accessTokenId) {
        this.onBehalfOf = accessTokenId;
    }

    /**
     * Clears the On-Behalf-Of value used with this client.
     */
    public void clearAccessToken() {
        this.onBehalfOf = null;
    }

    /**
     * Looks up member information for the current user. The user is defined by
     * the key used for authentication.
     *
     * @return member information
     */
    public Observable<Member> getMember() {
        return toObservable(gateway.getMember(GetMemberRequest.getDefaultInstance()))
                .map(GetMemberResponse::getMember);
    }

    /**
     * Adds a public key to the list of the approved keys.
     *
     * @param member member to add the key to
     * @param level key level
     * @param publicKey public key to add to the approved list
     * @return member information
     */
    public Observable<Member> addKey(
            Member member,
            Level level,
            byte[] publicKey) {
        return updateMember(MemberUpdate.newBuilder()
                .setMemberId(member.getId())
                .setPrevHash(member.getLastHash())
                .setAddKey(MemberAddKeyOperation.newBuilder()
                        .setPublicKey(ByteEncoding.serialize(publicKey))
                        .setLevel(level))
                .build());
    }

    /**
     * Removes a public key from the list of the approved keys.
     *
     * @param member member to remove the key for
     * @param keyId key ID of the key to remove
     * @return member information
     */
    public Observable<Member> removeKey(
            Member member,
            String keyId) {
        return updateMember(MemberUpdate.newBuilder()
                .setMemberId(member.getId())
                .setPrevHash(member.getLastHash())
                .setRemoveKey(MemberProtos.MemberRemoveKeyOperation.newBuilder()
                        .setKeyId(keyId))
                .build());
    }

    /**
     * Checks if a given alias already exists.
     *
     * @param alias alias to check
     * @return {@code true} if alias already exists, {@code false} otherwise
     */
    public Observable<Boolean> aliasExists(String alias) {
        return toObservable(gateway.aliasExists(AliasExistsRequest.newBuilder()
                .setAlias(alias)
                .build()))
                .map(AliasExistsResponse::getExists);
    }

    /**
     * Adds an alias for a given user.
     *
     * @param member member to add the key to
     * @param alias new unique alias to add
     * @return member information
     */
    public Observable<Member> addAlias(
            Member member,
            String alias) {
        return updateMember(MemberUpdate.newBuilder()
                .setMemberId(member.getId())
                .setPrevHash(member.getLastHash())
                .setAddAlias(MemberAliasOperation.newBuilder()
                        .setAlias(alias))
                .build());
    }

    /**
     * Removes an existing alias for a given user.
     *
     * @param member member to add the key to
     * @param alias new unique alias to add
     * @return member information
     */
    public Observable<Member> removeAlias(
            Member member,
            String alias) {
        return updateMember(MemberUpdate.newBuilder()
                .setMemberId(member.getId())
                .setPrevHash(member.getLastHash())
                .setRemoveAlias(MemberAliasOperation.newBuilder()
                        .setAlias(alias))
                .build());
    }

    /**
     * Subscribes a device to receive push notifications
     *
     * @param provider notification provider (e.g. Token)
     * @param notificationUri uri of the device (e.g. iOS push token)
     * @param platform platform of the device
     * @param tags tags for the device
     * @return nothing
     */
    public Observable<Void> subscribeDevice(
            String provider,
            String notificationUri,
            DeviceProtos.Platform platform,
            List<String> tags) {
        return toObservable(gateway.subscribeDevice(SubscribeDeviceRequest.newBuilder()
                .setProvider(provider)
                .setNotificationUri(notificationUri)
                .setPlatform(platform)
                .addAllTags(tags)
                .build()))
                .map(empty -> null);
    }

     /**
     * Unsubscribes a device from push notifications
     *
     * @param provider notification provider (e.g. Token)
     * @param notificationUri uri of the device (e.g. iOS push token)
     * @return nothing
     */
    public Observable<Void> unsubscribeDevice(
            String provider,
            String notificationUri) {
        return toObservable(gateway.unsubscribeDevice(UnsubscribeDeviceRequest.newBuilder()
                .setProvider(provider)
                .setNotificationUri(notificationUri)
                .build()))
                .map(empty -> null);
    }

    /**
     * Links a funding bank account to Token.
     *
     * @param bankId bank id
     * @param accountLinkPayload account link authorization payload generated
     *                           by the bank
     * @return list of linked accounts
     */
    public Observable<List<Account>> linkAccounts(
            String bankId,
            String accountLinkPayload) {
        return toObservable(gateway.linkAccounts(LinkAccountsRequest.newBuilder()
                .setBankId(bankId)
                .setAccountsLinkPayload(accountLinkPayload)
                .build())
        ).map(LinkAccountsResponse::getAccountsList);
    }

    /**
     * Looks up a linked funding account.
     *
     * @param accountId account id
     * @return account info
     */
    public Observable<Account> getAccount(String accountId) {
        setAuthenticationContext();
        return toObservable(gateway.getAccount(GetAccountRequest.newBuilder()
                .setAccountId(accountId)
                .build())
        ).map(GetAccountResponse::getAccount);
    }

    /**
     * Looks up all the linked funding accounts.
     *
     * @return list of linked accounts
     */
    public Observable<List<Account>> getAccounts() {
        setAuthenticationContext();
        return toObservable(gateway.getAccounts(GetAccountsRequest.newBuilder()
                .build())
        ).map(GetAccountsResponse::getAccountsList);
    }

    /**
     * Sets account name.
     *
     * @param accountId account id
     * @param accountName new name to use
     * @return updated account info
     */
    public Observable<Account> setAccountName(
            String accountId,
            String accountName) {
        return toObservable(gateway.setAccountName(SetAccountNameRequest.newBuilder()
                .setAccountId(accountId)
                .setName(accountName)
                .build())
        ).map(SetAccountNameResponse::getAccount);
    }

    /**
     * Creates a new token.
     *
     * @param payload payment token payload
     * @return payment token returned by the server
     */
    public Observable<Token> createPaymentToken(TokenPayload payload) {
        return toObservable(gateway.createPaymentToken(CreatePaymentTokenRequest.newBuilder()
                .setPayload(payload)
                .build())
        ).map(CreatePaymentTokenResponse::getToken);
    }

    /**
     * Creates a new access token.
     *
     * @param payload information token payload
     * @return the token returned by the server
     */
    public Observable<Token> createAccessToken(TokenPayload payload) {
        return toObservable(gateway.createAccessToken(CreateAccessTokenRequest.newBuilder()
                .setPayload(payload)
                .build())
        ).map(CreateAccessTokenResponse::getToken);
    }

    /**
     * Looks up a existing token.
     *
     * @param tokenId token id
     * @return token returned by the server
     */
    public Observable<Token> getPaymentToken(String tokenId) {
        return toObservable(gateway.getPaymentToken(GetPaymentTokenRequest.newBuilder()
                .setTokenId(tokenId)
                .build())
        ).map(GetPaymentTokenResponse::getToken);
    }

    /**
     * Looks up a list of existing token.
     *
     * @param offset offset to start at
     * @param limit max number of records to return
     * @return token returned by the server
     */
    public Observable<List<Token>> getPaymentTokens(int offset, int limit) {
        return toObservable(gateway.getPaymentTokens(GetPaymentTokensRequest.newBuilder()
                .setPage(Page.newBuilder()
                    .setOffset(Integer.toString(offset)) // TODO(maxim): Fix me
                    .setLimit(limit))
                .build())
        ).map(GetPaymentTokensResponse::getTokensList);
    }

    /**
     * Endorses a token.
     *
     * @param token token to endorse
     * @return endorsed token returned by the server
     */
    public Observable<Token> endorsePaymentToken(Token token) {
        return toObservable(gateway.endorsePaymentToken(EndorsePaymentTokenRequest.newBuilder()
                .setTokenId(token.getId())
                .setSignature(Signature.newBuilder()
                        .setKeyId(key.getId())
                        .setSignature(sign(key, token, ENDORSED)))
                .build())
        ).map(EndorsePaymentTokenResponse::getToken);
    }

    /**
     * Cancels a token.
     *
     * @param token token to cancel
     * @return cancelled token returned by the server
     */
    public Observable<Token> cancelPaymentToken(Token token) {
        return toObservable(gateway.cancelPaymentToken(CancelPaymentTokenRequest.newBuilder()
                .setTokenId(token.getId())
                .setSignature(Signature.newBuilder()
                        .setKeyId(key.getId())
                        .setSignature(sign(key, token, CANCELLED)))
                .build())
        ).map(CancelPaymentTokenResponse::getToken);
    }

    /**
     * Redeems a payment token.
     *
     * @param payment payment parameters, such as amount, currency, etc
     * @return payment record
     */
    public Observable<Payment> redeemPaymentToken(PaymentPayload payment) {
        return toObservable(gateway.redeemPaymentToken(RedeemPaymentTokenRequest.newBuilder()
                .setPayload(payment)
                .setPayloadSignature(Signature.newBuilder()
                        .setKeyId(key.getId())
                        .setSignature(sign(key, payment)))
                .build())
        ).map(RedeemPaymentTokenResponse::getPayment);
    }

    /**
     * Looks up account balance.
     *
     * @param accountId account id
     * @return account balance
     */
    public Observable<Money> getBalance(String accountId) {
        setAuthenticationContext();
        return toObservable(gateway.getBalance(GetBalanceRequest.newBuilder()
                .setAccountId(accountId)
                .build())
        ).map(GetBalanceResponse::getCurrent);
    }

    /**
     * Looks up an existing payment.
     *
     * @param paymentId payment id
     * @return payment record
     */
    public Observable<Payment> getPayment(String paymentId) {
        return toObservable(gateway.getPayment(GetPaymentRequest.newBuilder()
                .setPaymentId(paymentId)
                .build())
        ).map(GetPaymentResponse::getPayment);
    }

    /**
     * Looks up a list of existing payments.
     *
     * @param offset offset to start at
     * @param limit max number of records to return
     * @param tokenId optional token id to restrict the search
     * @return payment record
     */
    public Observable<List<Payment>> getPayments(
            int offset,
            int limit,
            @Nullable String tokenId) {
        GetPaymentsRequest.Builder request = GetPaymentsRequest.newBuilder()
                .setPage(Page.newBuilder()
                        .setOffset(Integer.toString(offset)) // TODO(maxim): Fix me
                        .setLimit(limit));

        if (tokenId != null) {
            request.setTokenId(tokenId);
        }

        return toObservable(gateway.getPayments(request.build()))
                .map(GetPaymentsResponse::getPaymentsList);
    }

    /**
     * Looks up an existing transaction. Doesn't have to be a transaction for a token payment.
     *
     * @param accountId ID of the account
     * @param transactionId ID of the transaction
     * @return transaction record
     */
    public Observable<Transaction> getTransaction(
            String accountId,
            String transactionId) {
        setAuthenticationContext();
        return toObservable(gateway.getTransaction(GetTransactionRequest.newBuilder()
                .setAccountId(accountId)
                .setTransactionId(transactionId)
                .build())
        ).map(GetTransactionResponse::getTransaction);
    }

    /**
     * Looks up existing transactions. This is a full list of transactions with token payments
     * being a subset.
     *
     * @param accountId ID of the account
     * @param offset offset to start at
     * @param limit max number of records to return
     * @return transaction record
     */
    public Observable<List<Transaction>> getTransactions(
            String accountId,
            int offset,
            int limit) {
        setAuthenticationContext();
        return toObservable(gateway.getTransactions(GetTransactionsRequest.newBuilder()
                .setAccountId(accountId)
                .setPage(Page.newBuilder()
                        .setOffset(Integer.toString(offset)) // TODO(maxim): Fix me
                        .setLimit(limit))
                .build())
        ).map(GetTransactionsResponse::getTransactionsList);
    }

    /**
     * Adds a new member address.
     *
     * @param name the name of the address
     * @param address the address json
     * @return an address record created
     */
    public Observable<Address> addAddress(
            String name,
            String address) {
        return toObservable(gateway.addAddress(AddAddressRequest.newBuilder()
                .setName(name)
                .setData(address)
                .setDataSignature(Signature.newBuilder()
                        .setKeyId(key.getId())
                        .setSignature(sign(key, address))
                        .build())
                .build())
        ).map(AddAddressResponse::getAddress);
    }

    /**
     * Looks up an address by id
     *
     * @param addressId the address id
     * @return an address record
     */
    public Observable<Address> getAddress(String addressId) {
        setAuthenticationContext();
        return toObservable(gateway.getAddress(GetAddressRequest.newBuilder()
                .setAddressId(addressId)
                .build())
        ).map(GetAddressResponse::getAddress);
    }

    /**
     * Looks up member addresses
     *
     * @return a list of addresses
     */
    public Observable<List<Address>> getAddresses() {
        setAuthenticationContext();
        return toObservable(gateway.getAddresses(GetAddressesRequest.newBuilder()
                .build())
        ).map(GetAddressesResponse::getAddressesList);
    }

    /**
     * Deletes a member address by its id
     *
     * @param addressId the id of the address
     */
    public Observable<Void> deleteAddress(String addressId) {
        return toObservable(gateway.deleteAddress(DeleteAddressRequest.newBuilder()
                .setAddressId(addressId)
                .build())
        ).map(empty -> null);
    }

    private Observable<Member> updateMember(MemberUpdate update) {
        return toObservable(gateway.updateMember(UpdateMemberRequest.newBuilder()
                .setUpdate(update)
                .setUpdateSignature(Signature.newBuilder()
                        .setKeyId(key.getId())
                        .setSignature(sign(key, update)))
                .build())
        ).map(UpdateMemberResponse::getMember);
    }

    private void setAuthenticationContext() {
        if(onBehalfOf != null) {
            AuthenticationContext.setOnBehalfOf(onBehalfOf);
        }
    }
}
