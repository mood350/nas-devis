-- DONNEES DE DEMONSTRATION (profil Spring "demo" uniquement).
-- Tous les prix sont FICTIFS, marques demo = true, et ne servent jamais a un devis emis.

insert into suppliers (name, address) values
    ('DEMO - Fournisseur A', 'Lome (fictif)'),
    ('DEMO - Fournisseur B', 'Lome (fictif)'),
    ('DEMO - Fournisseur C', 'Lome (fictif)');

insert into materials (reference, name, category_id, unit_id, characteristics, active)
select d.ref, d.name, c.id, u.id, d.spec, true
from (values
    ('CIM-CPJ425-50', 'Ciment CPJ 42,5 - sac de 50 kg', 'Gros oeuvre', 'sac', 'CPJ 42,5 ; sac 50 kg'),
    ('FER-HA8',  'Fer à béton HA Ø8 mm - barre de 12 m',  'Gros oeuvre', 'barre', 'HA ; Ø8 mm ; barre 12 m'),
    ('FER-HA10', 'Fer à béton HA Ø10 mm - barre de 12 m', 'Gros oeuvre', 'barre', 'HA ; Ø10 mm ; barre 12 m'),
    ('FER-HA12', 'Fer à béton HA Ø12 mm - barre de 12 m', 'Gros oeuvre', 'barre', 'HA ; Ø12 mm ; barre 12 m'),
    ('FER-HA14', 'Fer à béton HA Ø14 mm - barre de 12 m', 'Gros oeuvre', 'barre', 'HA ; Ø14 mm ; barre 12 m'),
    ('FER-HA16', 'Fer à béton HA Ø16 mm - barre de 12 m', 'Gros oeuvre', 'barre', 'HA ; Ø16 mm ; barre 12 m'),
    ('SAB-LAG',  'Sable de lagune', 'Gros oeuvre', 'm3', 'sable de lagune'),
    ('GRA-1525', 'Gravier concassé 15/25', 'Gros oeuvre', 'm3', 'concassé 15/25'),
    ('PARP-10',  'Parpaing creux 10x20x40 cm', 'Gros oeuvre', 'u', 'creux ; 10x20x40 cm'),
    ('PARP-15',  'Parpaing creux 15x20x40 cm', 'Gros oeuvre', 'u', 'creux ; 15x20x40 cm'),
    ('PARP-20',  'Parpaing creux 20x20x40 cm', 'Gros oeuvre', 'u', 'creux ; 20x20x40 cm'),
    ('BOIS-COF', 'Planche de coffrage 25x150 mm - 4 m', 'Gros oeuvre', 'u', 'planche 25x150 mm ; 4 m'),
    ('PVC-EVAC-50',  'Tuyau PVC évacuation Ø50 mm - barre de 6 m',  'Plomberie', 'barre', 'PVC évacuation ; Ø50 mm ; barre 6 m'),
    ('PVC-EVAC-100', 'Tuyau PVC évacuation Ø100 mm - barre de 6 m', 'Plomberie', 'barre', 'PVC évacuation ; Ø100 mm ; barre 6 m'),
    ('CAB-ELEC-15', 'Câble électrique 1,5 mm² (au mètre)', 'Electricite', 'm', 'cuivre ; 1,5 mm²'),
    ('CAB-ELEC-25', 'Câble électrique 2,5 mm² (au mètre)', 'Electricite', 'm', 'cuivre ; 2,5 mm²')
) as d(ref, name, cat, unit, spec)
join categories c on c.name = d.cat
join measure_units u on u.code = d.unit;

insert into price_observations (material_id, supplier_id, locality, sale_unit_id, unit_price, observed_on,
                                source_type, status, demo, characteristics, source_reference, created_at, validated_at)
select m.id, s.id, 'Lomé', m.unit_id, round(p.price * f.factor, 0), current_date,
       'COMMERCIAL', 'VALIDATED', true, m.characteristics, 'DEMO - valeur fictive', current_timestamp, current_timestamp
from (values
    ('CIM-CPJ425-50', 5000), ('FER-HA8', 1800), ('FER-HA10', 3000), ('FER-HA12', 4300), ('FER-HA14', 5900),
    ('FER-HA16', 7600), ('SAB-LAG', 9000), ('GRA-1525', 18000), ('PARP-10', 350), ('PARP-15', 450),
    ('PARP-20', 550), ('BOIS-COF', 1800), ('PVC-EVAC-50', 3500), ('PVC-EVAC-100', 8500),
    ('CAB-ELEC-15', 300), ('CAB-ELEC-25', 450)
) as p(ref, price)
join materials m on m.reference = p.ref
cross join (values ('DEMO - Fournisseur A', 1.00), ('DEMO - Fournisseur B', 1.05), ('DEMO - Fournisseur C', 0.96))
    as f(supplier, factor)
join suppliers s on s.name = f.supplier;
