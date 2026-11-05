package com.sakasmart;

/** Plaintext code exists only transiently while being submitted to the provider. */
public record RecoveryDelivery(String requestId, String destination, String code) { }
