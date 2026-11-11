-- Devis crees avant V3 : net_ht et tax_total valaient 0 (valeur par defaut) alors que total_ttc etait renseigne.
-- net_ht = sous-total - remise ; la taxe (ancienne TVA, supprimee par V3) = total_ttc - net_ht.
-- Le detail par taux n'existe plus (vat_rate supprime) : quote_taxes reste vide pour ces devis.
-- Idempotent : un devis recent a net_ht > 0 des que total_ttc > 0, il n'est jamais touche.
update quotes
set net_ht = subtotal - discount_amount,
    tax_total = total_ttc - (subtotal - discount_amount)
where net_ht = 0 and total_ttc > 0;
