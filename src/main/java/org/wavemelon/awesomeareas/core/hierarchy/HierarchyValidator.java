package org.wavemelon.awesomeareas.core.hierarchy;

import org.wavemelon.awesomeareas.core.area.Area;
import org.wavemelon.awesomeareas.core.area.AreaType;

import java.util.UUID;
import java.util.function.Function;

/**
 * Validates area hierarchy and geometric containment constraints.
 */
public class HierarchyValidator {

    public record ValidationResult(boolean valid, String message) {
        public static ValidationResult ok() {
            return new ValidationResult(true, "OK");
        }

        public static ValidationResult success() {
            return ok();
        }

        public static ValidationResult error(String message) {
            return new ValidationResult(false, message);
        }

        public static ValidationResult invalid(String message) {
            return error(message);
        }
    }

    public static ValidationResult validate(Area area, Function<UUID, Area> areaResolver) {
        if (area == null) {
            return ValidationResult.invalid("Area cannot be null.");
        }
        Area parent = (area.getParentId() != null && areaResolver != null) ? areaResolver.apply(area.getParentId()) : null;
        return validateParentChild(area, parent, areaResolver);
    }

    /**
     * Validates whether an area can have the specified parent.
     */
    public static ValidationResult validateParentChild(Area child, Area parent, Function<UUID, Area> areaResolver) {
        if (child == null) {
            return ValidationResult.invalid("Child area cannot be null.");
        }

        AreaType childType = child.getType();

        // 1. Root / Standalone checks
        if (parent == null) {
            if (!childType.canExistStandalone()) {
                return ValidationResult.invalid(childType.getDefaultDisplayName() + " cannot exist outside a parent area.");
            }
            return ValidationResult.success();
        }

        AreaType parentType = parent.getType();

        // 2. Cycle detection
        if (child.getId().equals(parent.getId())) {
            return ValidationResult.invalid("An area cannot be its own parent.");
        }

        Area current = parent;
        while (current != null && current.getParentId() != null) {
            if (current.getParentId().equals(child.getId())) {
                return ValidationResult.invalid("Circular hierarchy detected! " + child.getName() + " is an ancestor of " + parent.getName() + ".");
            }
            current = areaResolver != null ? areaResolver.apply(current.getParentId()) : null;
        }

        // 3. Hierarchy type rules
        switch (childType) {
            case COUNTRY -> {
                return ValidationResult.invalid("A Country cannot be placed inside another area.");
            }
            case PROVINCE -> {
                if (parentType != AreaType.COUNTRY) {
                    return ValidationResult.invalid("A Province/State can only exist directly inside a Country (parent is " + parentType.getDefaultDisplayName() + ").");
                }
            }
            case COUNTY -> {
                if (parentType != AreaType.PROVINCE) {
                    return ValidationResult.invalid("A County/District can only exist directly inside a Province/State (parent is " + parentType.getDefaultDisplayName() + ").");
                }
            }
            case CITY -> {
                if (parentType != AreaType.COUNTRY && parentType != AreaType.PROVINCE && parentType != AreaType.COUNTY && parentType != AreaType.TRIBE) {
                    return ValidationResult.invalid("A City can only be placed inside a Country, Province, County, or Tribe/Faction.");
                }
            }
            case DISTRICT -> {
                if (parentType != AreaType.CITY) {
                    return ValidationResult.invalid("A District/Neighborhood can only exist directly inside a City.");
                }
            }
            case TRIBE -> {
                if (parentType != AreaType.CITY) {
                    return ValidationResult.invalid("A Tribe/Gang/Faction can only exist standalone or inside a City.");
                }
            }
        }

        // 4. In reverse: check if parent can contain this child
        if (parentType == AreaType.TRIBE && childType != AreaType.CITY) {
            return ValidationResult.invalid("A Tribe/Faction can only contain Villages/Cities, not " + childType.getDefaultDisplayName() + ".");
        }

        // 5. Geographic boundary check: child boundary must be inside parent boundary
        if (child.getBoundary() != null && parent.getBoundary() != null) {
            if (!parent.getBoundary().contains(child.getBoundary())) {
                return ValidationResult.invalid("The boundary of " + child.getName() + " is not fully contained within parent " + parent.getName() + ".");
            }
        }

        return ValidationResult.success();
    }
}
