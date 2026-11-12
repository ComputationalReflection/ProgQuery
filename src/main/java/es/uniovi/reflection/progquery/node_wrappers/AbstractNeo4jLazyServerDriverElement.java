package es.uniovi.reflection.progquery.node_wrappers;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

abstract class AbstractNeo4jLazyServerDriverElement implements Propertiable {

	Map<String, Object> properties = new HashMap<String, Object>();

    protected AbstractNeo4jLazyServerDriverElement canonicalElement() { return this; }
    protected GraphAccess propertyAccess() { return null; }

	public AbstractNeo4jLazyServerDriverElement(Object... props) {
		if (props == null || props.length % 2 != 0)
			throw new IllegalArgumentException("Properties must be supplied as name-value pairs.");
		for (int i = 0; i < props.length; i = i + 2)
			properties.put(props[i].toString(), props[i + 1]);
	}

	public void setProp(String name, Object value) {
        GraphAccess access = propertyAccess();
        if (access != null) access.topology.readLock().lock();
        try {
            var element = canonicalElement();
            synchronized (element) { element.properties.put(name, value); }
        } finally { if (access != null) access.topology.readLock().unlock(); }
	}

	public boolean hasProperty(String name) {
        return getProperty(name) != null;
	}

	public Object getProperty(String name) {

        GraphAccess access = propertyAccess();
        if (access != null) access.topology.readLock().lock();
        try {
            var element = canonicalElement();
            synchronized (element) { return element.properties.get(name); }
        } finally { if (access != null) access.topology.readLock().unlock(); }
	}

	public Set<Entry<String, Object>> getAllProperties() {
        GraphAccess access = propertyAccess();
        if (access != null) access.topology.readLock().lock();
        try {
            var element = canonicalElement();
            synchronized (element) { return new HashMap<>(element.properties).entrySet(); }
        } finally { if (access != null) access.topology.readLock().unlock(); }
	}

}
