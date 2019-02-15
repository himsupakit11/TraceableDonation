package com.template

import net.corda.core.contracts.*
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.transactions.LedgerTransaction
import java.security.PublicKey
import java.time.Instant
import java.util.*


// ************
// * Contract *
// ************
class CampaignContract : Contract {
    companion object {
        // Used to identify our contract when building a transaction.
        const val ID = "com.template.CampaignContract"
    }
    
    // A transaction is valid if the verify() function of the contract of all the transaction's input and output states
    // does not throw an exception.
    override fun verify(tx: LedgerTransaction) {
        val campaignCommand = tx.commands.requireSingleCommand<Commands>()
        val setOfSigners = campaignCommand.signers.toSet()

        when(campaignCommand.value){
            is Commands.Start -> verifyStrart(tx,setOfSigners)
            else -> throw IllegalArgumentException("")
        }
    }

    private fun verifyStrart(tx: LedgerTransaction,signers: Set<PublicKey>) = requireThat {
        "No input states should be consumed when creating a campaign." using(tx.inputStates.isEmpty())
        "Only one campaign state should be produced when creating a campaign." using (tx.outputStates.size == 1)
        val campaign = tx.outputStates.single() as Campaign
        "The target field of a recently created campaign should be a positive value." using (campaign.target > Amount(0,campaign.target.token))
        "There raised field must be 0 when starting a campaign." using(campaign.raised == Amount(0,campaign.target.token))
        "The campaign deadline must be in the future." using (campaign.deadline > Instant.now())
        "There must be a campaign name." using (campaign.name != "")
        "The campaign must only be signed by fundraiser" using (signers == setOf(campaign.fundraiser.owningKey) )
        "There must be a campaign category" using (campaign.category != "")

    }

    // Used to indicate the transaction's intent.
    interface Commands : CommandData {
        class Start : TypeOnlyCommandData(),Commands
        class End : TypeOnlyCommandData(), Commands
        class AcceptDonation: TypeOnlyCommandData(), Commands
    }
}
// Return public key of participants
fun keysFromParticipants(obligation: ContractState): Set<PublicKey>{
    return obligation.participants.map { it.owningKey }.toSet()
}

// *********
// * State *
// *********
data class Campaign(
        val name: String,
        val target: Amount<Currency>,
        val raised: Amount<Currency> = Amount(0,target.token),
        val fundraiser: Party,
        val recipient: Party,
        val deadline: Instant,
        val category: String,
        override val participants: List<AbstractParty > = listOf(fundraiser,recipient),
        override val linearId: UniqueIdentifier = UniqueIdentifier()
) : LinearState//,SchedulableState {
//    override fun nextScheduledActivity(thisStateRef: StateRef, flowLogicRefFactory: FlowLogicRefFactory): ScheduledActivity? {
//        return ScheduledActivity(flowLogicRefFactory.create(EndCampaign.Initator::class.java,thisStateRef),deadline)
//    }

//}
