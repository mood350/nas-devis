-- Verrou optimiste : deux validations/rejets simultanes d'une meme observation ne peuvent plus reussir tous les deux.
alter table price_observations add column version bigint not null default 0;
