import xml.etree.ElementTree as ET

tree = ET.parse('build/reports/jacoco/test/jacocoTestReport.xml')
root = tree.getroot()

for pkg in root.findall('package'):
    for cls in pkg.findall('class'):
        cls_name = cls.attrib['name']
        if 'FacturaService' in cls_name or 'InventarioService' in cls_name or 'VentaService' in cls_name:
            print(f"\nClass: {cls_name}")
            for method in cls.findall('method'):
                m_name = method.attrib['name']
                for c in method.findall('counter'):
                    if c.attrib['type'] == 'LINE' and int(c.attrib['missed']) > 0:
                        print(f"  Method {m_name}: missed {c.attrib['missed']} lines out of {int(c.attrib['missed'])+int(c.attrib['covered'])}")
