package monero.wallet.lws.wire

/**
 * Models the exchange rates of Monero against other currencies, which the server fills from the daemon behind it when
 * it has them. The name of every field is the code of a currency, exactly as the server writes it (rates.h:37-58), and
 * the server leaves the whole field out of a response when the daemon provides no rates.
 */
class MoneroLwsRates {
    var AUD: Double? = null
    var BRL: Double? = null
    var BTC: Double? = null
    var CAD: Double? = null
    var CHF: Double? = null
    var CNY: Double? = null
    var EUR: Double? = null
    var GBP: Double? = null
    var HKD: Double? = null
    var INR: Double? = null
    var JPY: Double? = null
    var KRW: Double? = null
    var MXN: Double? = null
    var NOK: Double? = null
    var NZD: Double? = null
    var SEK: Double? = null
    var SGD: Double? = null
    var TRY: Double? = null
    var USD: Double? = null
    var RUB: Double? = null
    var ZAR: Double? = null

    constructor()

    constructor(other: MoneroLwsRates) {
        this.AUD = other.AUD
        this.BRL = other.BRL
        this.BTC = other.BTC
        this.CAD = other.CAD
        this.CHF = other.CHF
        this.CNY = other.CNY
        this.EUR = other.EUR
        this.GBP = other.GBP
        this.HKD = other.HKD
        this.INR = other.INR
        this.JPY = other.JPY
        this.KRW = other.KRW
        this.MXN = other.MXN
        this.NOK = other.NOK
        this.NZD = other.NZD
        this.SEK = other.SEK
        this.SGD = other.SGD
        this.TRY = other.TRY
        this.USD = other.USD
        this.RUB = other.RUB
        this.ZAR = other.ZAR
    }

    fun copy(): MoneroLwsRates {
        return MoneroLwsRates(this)
    }
}
