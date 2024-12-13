The standard names for indexes in PostgreSQL are::

    {tablename}_{columnname(s)}_{suffix}

where the suffix is one of the following:

    * ``pkey`` for a Primary Key constraint;
    * ``key`` for a Unique constraint;
    * ``excl`` for an Exclusion constraint;
    * ``idx`` for any other kind of index;
    * ``fkey`` for a Foreign key;
    * ``check`` for a Check constraint;

Standard suffix for sequences is

    ``seq`` for all sequences

Found `here`_

.. _here: http://stackoverflow.com/questions/4107915/postgresql-default-constraint-names/4108266#4108266