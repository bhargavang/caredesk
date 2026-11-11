-- One row that records when the demo data was last generated. Instances lock it while
-- seeding, so several of them starting at once never seed or refresh twice.
create table demo_state (
    id           int primary key,
    refreshed_on date
);

insert into demo_state (id, refreshed_on) values (1, null);
