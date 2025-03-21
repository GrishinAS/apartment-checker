package com.grishin.apartment.checker.storage;



import com.grishin.apartment.checker.storage.entity.Unit;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

import java.util.List;
import java.util.stream.DoubleStream;

public interface ApartmentsRepository extends CrudRepository<Unit, Long> {
    @Query("select p.id from #{#entityName} p")
    List<Long> findAllIds();
}
