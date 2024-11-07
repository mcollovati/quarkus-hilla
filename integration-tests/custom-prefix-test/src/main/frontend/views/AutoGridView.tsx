import { AutoGrid } from '@vaadin/hilla-react-crud';
import { UserService } from 'Frontend/generated/endpoints';
import UserPOJOModel from 'Frontend/generated/com/example/application/autocrud/UserPOJOModel';
import { ViewConfig } from '@vaadin/hilla-file-router/types.js';

export const config: ViewConfig = {
    title: "Autogrid",
    route: "auto-grid"
};

export default function AutoGridView() {
    return <AutoGrid service={UserService} model={UserPOJOModel}
                     visibleColumns={['id', 'name', 'surname']} />;
}


