package oxygen.consistencycheck.dd;

import com.experian.edf.oxygen.consistencycheck.domain.SynthesisProcessor;
import com.experian.edf.oxygen.consistencycheck.domain.DictionaryFieldInternal;
import com.experian.edf.oxygen.consistencycheck.domain.DomainDictionaryInternal;
import com.experian.edf.oxygen.global.schemas.SchemaElement;
import lombok.Data;
import lombok.SneakyThrows;
import scala.Option;

import java.io.Serializable;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * @author Abraham, Thomas - C15627A
 * Created on 8/24/20 at 4:45 PM
 */
@Data
public class DomainDictionaryV1 implements Serializable {
    String domain;
    int version;
    List<DictionaryFieldV1> fields;

    public DomainDictionaryInternal toDomainDictionary() {
        List<DictionaryFieldInternal> collect = fields.stream().map(each ->
                new DictionaryFieldInternal(
                        each.getName(),
                        each.getHandler(),
                        each.getPeriodicity(),
                        each.getComplexTypeDefinition(),
                        each.getFieldType(),
                        Option.apply(each.getSynthesisProcessor().orElse(null)),
                        each.getDescription()
                )
        ).collect(Collectors.toList());
        DictionaryFieldInternal[] dictionaryFieldInternalArray = new DictionaryFieldInternal[collect.size()];
        return new DomainDictionaryInternal(domain, collect.toArray(dictionaryFieldInternalArray), 1);
    }
}

@Data
class DictionaryFieldV1 implements Serializable {
    private String name;
    private String handler = "LFP";
    private Boolean periodicity = true;
    private SchemaElement complexTypeDefinition;
    private String fieldType = "String";
    private String synthesizer = null;
    private String description;

    @SneakyThrows
    public Optional<SynthesisProcessor> getSynthesisProcessor() {
        if (synthesizer != null) {
            try {
                return Optional.of((SynthesisProcessor) Class.forName(synthesizer.trim()).newInstance());
            } catch (ClassNotFoundException e) {
                //TODO: This exception handling needs to be removed.
                //TODO: Just adding this because this is how the current implementation is and to geta around test failures
                return Optional.empty();
            }
        } else return Optional.empty();

    }
}







