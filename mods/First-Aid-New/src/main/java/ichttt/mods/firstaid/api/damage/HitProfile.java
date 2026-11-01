// AGENT-DONE(claude): shell-ballistics
package ichttt.mods.firstaid.api.damage;

/**
 * How one kind of hit (a rifle round, a tank shell) differs from the plain damage of a damage type. Other mods describe
 * their ammunition through {@link HitProfiles}; First Aid itself only knows these numbers, not what ammunition is.
 *
 * @param damageMultiplier    scales the amount of the hit
 * @param damageIsLimbHp      true if the amount already means limb hit points instead of vanilla health
 * @param armorPierce         0..1, the share of the damage that ignores armor
 * @param bleedChanceBonus    added to the chance that the hit opens a bleed
 * @param heavyBleedHitFraction share of a limb that has to be lost for a heavy bleed, negative for the configured value
 * @param fractureChanceBonus added to the chance that the hit breaks a bone
 * @param overkillFactor      share of the excess of an emptied limb that is passed on, negative for the configured value
 * @param explosive           blast injuries (fractures like an explosion, always able to bleed)
 */
public record HitProfile(
        float damageMultiplier,
        boolean damageIsLimbHp,
        float armorPierce,
        float bleedChanceBonus,
        float heavyBleedHitFraction,
        float fractureChanceBonus,
        float overkillFactor,
        boolean explosive) {

    public static final HitProfile NEUTRAL = new HitProfile(1.0F, false, 0.0F, 0.0F, -1.0F, 0.0F, -1.0F, false);

    public float heavyBleedHitFractionOr(float configured) {
        return heavyBleedHitFraction < 0.0F ? configured : heavyBleedHitFraction;
    }

    public float overkillFactorOr(float configured) {
        return overkillFactor < 0.0F ? configured : overkillFactor;
    }
}
