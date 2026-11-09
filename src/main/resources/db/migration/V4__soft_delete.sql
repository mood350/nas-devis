-- Suppression douce : la ligne reste en base, les requetes JPA l'ignorent (@SQLRestriction).
alter table quote_templates add column deleted_at timestamp;
alter table quotes add column deleted_at timestamp;
