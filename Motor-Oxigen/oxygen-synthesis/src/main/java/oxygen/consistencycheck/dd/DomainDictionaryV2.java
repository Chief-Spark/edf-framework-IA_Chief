package oxygen.consistencycheck.dd;

import com.experian.edf.oxygen.consistencycheck.domain.SynthesisProcessor;
import com.experian.edf.oxygen.consistencycheck.domain.DictionaryFieldInternal;
import com.experian.edf.oxygen.consistencycheck.domain.DomainDictionaryInternal;
import com.experian.edf.oxygen.global.domain.Datatype;
import com.experian.edf.oxygen.global.schemas.SchemaElement;
import lombok.Data;
import lombok.SneakyThrows;
import scala.Option;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * @author Abraham, Thomas - C15627A
 * Created on 8/24/20 at 4:45 PM
 */
@Data
public class DomainDictionaryV2 implements Serializable {
    String domain;
    int version;
    List<DictionaryFieldV2> fields = new ArrayList<>();

    public DomainDictionaryInternal toDomainDictionary() {
        List<DictionaryFieldInternal> collect = fields.stream().filter(DictionaryFieldV2::getEnabled).map(each ->
                new DictionaryFieldInternal(
                        each.getName(),
                        each.getHandler().identifier(),
                        each.getPeriodicity(),
                        each.getComplexTypeDefinition(),
                        each.getDataType().identifier(),
                        Option.apply(each.getSynthesisProcessor().orElse(null)),
                        each.getDescription()
                )
        ).collect(Collectors.toList());
        DictionaryFieldInternal[] dictionaryFieldInternalArray = new DictionaryFieldInternal[collect.size()];
        return new DomainDictionaryInternal(domain, collect.toArray(dictionaryFieldInternalArray), 1);
    }
}

@Data
class DictionaryFieldV2 implements Serializable {
    private String name;
    private DomainDictionaryFieldHandler handler = DomainDictionaryFieldHandler.LFP;
    private Boolean periodicity = true;
    private SchemaElement complexTypeDefinition;
    private Datatype dataType = Datatype.STRING;
    private String synthesizer = null;
    private String description;
    private String section = "default";
    private Boolean enabled = true;

    @SneakyThrows
    public Optional<SynthesisProcessor> getSynthesisProcessor() {
        if (synthesizer != null)
            return Optional.of((SynthesisProcessor) Class.forName(synthesizer.trim()).newInstance());
        else return Optional.empty();

    }
}







