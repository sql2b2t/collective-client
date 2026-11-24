package me.collective.client.integrations

interface ProtocolVersionAccess {
   fun olderEq_1_20_6(): Boolean

   fun olderEq_1_21_4(): Boolean

   fun olderEq_1_21_5(): Boolean
}
