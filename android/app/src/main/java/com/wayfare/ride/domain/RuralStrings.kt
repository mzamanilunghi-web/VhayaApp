package com.wayfare.ride.domain

data class RuralLocalizedStrings(
    val readyToRollPrefix: String,
    val readyGeneric: String,
    val chooseDestinationHint: String,
    val whereToLabel: String,
    val scheduleLaterLabel: String,
    val pickupGpsLabel: String,
    val fareLockedBadge: String,
    val emergency112Label: String,
    val smsTripFallbackLabel: String,
    val lowDataBadgeLabel: String
)

object RuralStrings {
    fun forLanguage(language: AppLanguage): RuralLocalizedStrings = when (language) {
        AppLanguage.ENGLISH -> RuralLocalizedStrings(
            readyToRollPrefix = "Ready to roll",
            readyGeneric = "Ready for a ride?",
            chooseDestinationHint = "Choose a destination or village landmark",
            whereToLabel = "Where to?",
            scheduleLaterLabel = "Later",
            pickupGpsLabel = "GPS pickup",
            fareLockedBadge = "Fare locked",
            emergency112Label = "112",
            smsTripFallbackLabel = "SMS trip",
            lowDataBadgeLabel = "Low-Data"
        )
        AppLanguage.SEPEDI -> RuralLocalizedStrings(
            readyToRollPrefix = "Re loketše go sepela",
            readyGeneric = "O nyaka senamelwa?",
            chooseDestinationHint = "Kgetha moo o yago goba lefelo la motse",
            whereToLabel = "O ya kae?",
            scheduleLaterLabel = "Ka morago",
            pickupGpsLabel = "GPS ya mo o lego",
            fareLockedBadge = "Tefo e tiišitšwe",
            emergency112Label = "Thuša 112",
            smsTripFallbackLabel = "SMS ya leeto",
            lowDataBadgeLabel = "Data e nyane"
        )
        AppLanguage.TSHIVENDA -> RuralLocalizedStrings(
            readyToRollPrefix = "Ro lugela u tshimbila",
            readyGeneric = "Ni khou ṱoḓa lwendo?",
            chooseDestinationHint = "Nangani hune na ya kana tshiga tsha muvhundu",
            whereToLabel = "Ni ya ngafhi?",
            scheduleLaterLabel = "Ngavhuya",
            pickupGpsLabel = "GPS ya henefha",
            fareLockedBadge = "Mutengo wo khwaṱhiswa",
            emergency112Label = "Thuso 112",
            smsTripFallbackLabel = "SMS ya lwendo",
            lowDataBadgeLabel = "Data ṱhukhu"
        )
        AppLanguage.XITSONGA -> RuralLocalizedStrings(
            readyToRollPrefix = "Hi lunghekile ku famba",
            readyGeneric = "U lava riendzo?",
            chooseDestinationHint = "Hlawula laha u yaka kumbe ndhawu ya tiko",
            whereToLabel = "U ya kwihi?",
            scheduleLaterLabel = "Endzhaku",
            pickupGpsLabel = "GPS ya laha",
            fareLockedBadge = "Mali yi tiyisiwile",
            emergency112Label = "Mpfuno 112",
            smsTripFallbackLabel = "SMS ya riendzo",
            lowDataBadgeLabel = "Data yitsongo"
        )
    }
}
